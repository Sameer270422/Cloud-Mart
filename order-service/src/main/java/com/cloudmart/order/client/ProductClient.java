package com.cloudmart.order.client;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Thin HTTP client to product-service. In a larger system this would use
 * a service-discovery-aware client (e.g. Spring Cloud OpenFeign + Eureka);
 * kept simple here for readability.
 */
@Component
public class ProductClient {

    private final RestTemplate restTemplate;
    private final String productServiceUrl;

    public ProductClient(RestTemplate restTemplate,
                          @Value("${cloudmart.services.product-service-url}") String productServiceUrl) {
        this.restTemplate = restTemplate;
        this.productServiceUrl = productServiceUrl;
    }

    public ProductDto getProduct(Long productId) {
        return restTemplate.getForObject(productServiceUrl + "/api/products/" + productId, ProductDto.class);
    }

    public void reserveStock(Long productId, int quantity) {
        restTemplate.postForObject(
                productServiceUrl + "/api/products/" + productId + "/reserve",
                Map.of("quantity", quantity),
                Void.class);
    }

    /**
     * Compensating call: undoes a previous reserveStock. Used when a later
     * item in the same order fails, so earlier reservations don't leak.
     */
    public void releaseStock(Long productId, int quantity) {
        restTemplate.postForObject(
                productServiceUrl + "/api/products/" + productId + "/release",
                Map.of("quantity", quantity),
                Void.class);
    }

    @Data
    public static class ProductDto {
        private Long id;
        private String name;
        private BigDecimal price;
        private Integer stockQuantity;
    }
}
