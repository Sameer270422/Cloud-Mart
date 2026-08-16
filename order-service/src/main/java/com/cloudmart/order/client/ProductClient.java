package com.cloudmart.order.client;

import com.cloudmart.order.exception.ProductServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Thin HTTP client to product-service. In a larger system this would use
 * a service-discovery-aware client (e.g. Spring Cloud OpenFeign + Eureka);
 * kept simple here for readability.
 *
 * Calls are bounded by RestTemplate connect/read timeouts and wrapped in a
 * retry + circuit breaker (config: "productService" in application.yml) so
 * a slow or down product-service degrades order-service instead of hanging
 * its request threads indefinitely.
 */
@Component
public class ProductClient {

    private static final String RESILIENCE_INSTANCE = "productService";

    private final RestTemplate restTemplate;
    private final String productServiceUrl;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ProductClient(RestTemplate restTemplate,
                          @Value("${cloudmart.services.product-service-url}") String productServiceUrl,
                          CircuitBreakerRegistry circuitBreakerRegistry,
                          RetryRegistry retryRegistry) {
        this.restTemplate = restTemplate;
        this.productServiceUrl = productServiceUrl;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(RESILIENCE_INSTANCE);
        this.retry = retryRegistry.retry(RESILIENCE_INSTANCE);
    }

    public ProductDto getProduct(Long productId) {
        try {
            return execute(() -> restTemplate.getForObject(
                    productServiceUrl + "/api/products/" + productId, ProductDto.class));
        } catch (HttpClientErrorException.NotFound ex) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }
    }

    public void reserveStock(Long productId, int quantity) {
        execute(() -> restTemplate.postForObject(
                productServiceUrl + "/api/products/" + productId + "/reserve",
                Map.of("quantity", quantity),
                Void.class));
    }

    /**
     * Compensating call: undoes a previous reserveStock. Used when a later
     * item in the same order fails, so earlier reservations don't leak.
     */
    public void releaseStock(Long productId, int quantity) {
        execute(() -> restTemplate.postForObject(
                productServiceUrl + "/api/products/" + productId + "/release",
                Map.of("quantity", quantity),
                Void.class));
    }

    // Client errors (4xx) are a real business response from product-service
    // (e.g. "not found", "insufficient stock") and are excluded from
    // retry/circuit-breaker accounting via resilience4j's ignoreExceptions
    // config - they propagate immediately, unwrapped, on the first attempt.
    // Anything else (timeout, connection refused, 5xx, or the breaker being
    // open) means product-service is genuinely unreachable right now.
    private <T> T execute(Supplier<T> call) {
        Supplier<T> decorated = CircuitBreaker.decorateSupplier(circuitBreaker,
                Retry.decorateSupplier(retry, call));
        try {
            return decorated.get();
        } catch (CallNotPermittedException ex) {
            throw new ProductServiceUnavailableException(
                    "product-service circuit breaker is open", ex);
        } catch (HttpClientErrorException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ProductServiceUnavailableException(
                    "product-service call failed after retries", ex);
        }
    }

    @Data
    public static class ProductDto {
        private Long id;
        private String name;
        private BigDecimal price;
        private Integer stockQuantity;
    }
}
