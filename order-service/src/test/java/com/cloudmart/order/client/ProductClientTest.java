package com.cloudmart.order.client;

import com.cloudmart.order.exception.ProductServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProductClientTest {

    private static final String BASE_URL = "http://product-service";

    private RestTemplate restTemplate;
    private ProductClient productClient;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(ResourceAccessException.class)
                .ignoreExceptions(HttpClientErrorException.class)
                .build();

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .ignoreExceptions(HttpClientErrorException.class)
                .build();

        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(cbConfig);
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);

        productClient = new ProductClient(restTemplate, BASE_URL, circuitBreakerRegistry, retryRegistry);
    }

    @Test
    void retriesOnTransientFailureThenSucceeds() {
        var product = new ProductClient.ProductDto();
        product.setId(1L);

        when(restTemplate.getForObject(anyString(), eq(ProductClient.ProductDto.class)))
                .thenThrow(new ResourceAccessException("connect timed out"))
                .thenThrow(new ResourceAccessException("connect timed out"))
                .thenReturn(product);

        ProductClient.ProductDto result = productClient.getProduct(1L);

        assertThat(result.getId()).isEqualTo(1L);
        verify(restTemplate, times(3)).getForObject(anyString(), eq(ProductClient.ProductDto.class));
    }

    @Test
    void throwsProductServiceUnavailableAfterRetriesExhausted() {
        when(restTemplate.getForObject(anyString(), eq(ProductClient.ProductDto.class)))
                .thenThrow(new ResourceAccessException("connect timed out"));

        assertThatThrownBy(() -> productClient.getProduct(1L))
                .isInstanceOf(ProductServiceUnavailableException.class);

        verify(restTemplate, times(3)).getForObject(anyString(), eq(ProductClient.ProductDto.class));
    }

    @Test
    void notFoundIsTranslatedImmediatelyWithoutRetrying() {
        var notFound = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);
        when(restTemplate.getForObject(anyString(), eq(ProductClient.ProductDto.class)))
                .thenThrow(notFound);

        assertThatThrownBy(() -> productClient.getProduct(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found");

        verify(restTemplate, times(1)).getForObject(anyString(), eq(ProductClient.ProductDto.class));
    }

    @Test
    void circuitBreakerOpensAfterRepeatedFailuresAndStopsCallingDownstream() {
        when(restTemplate.getForObject(anyString(), eq(ProductClient.ProductDto.class)))
                .thenThrow(new ResourceAccessException("connect timed out"));

        // 4 calls at 3 attempts each = 12 recorded failures, well past the
        // sliding window of 4 needed to trip the breaker.
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> productClient.getProduct(1L))
                    .isInstanceOf(ProductServiceUnavailableException.class);
        }

        int callsSoFar = mockingDetails(restTemplate).getInvocations().size();
        reset(restTemplate);

        assertThatThrownBy(() -> productClient.getProduct(1L))
                .isInstanceOf(ProductServiceUnavailableException.class);

        verifyNoInteractions(restTemplate);
        assertThat(callsSoFar).isGreaterThan(0);
    }
}
