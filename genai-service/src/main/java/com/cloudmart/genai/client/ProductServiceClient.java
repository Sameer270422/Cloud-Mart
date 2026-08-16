package com.cloudmart.genai.client;

import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class ProductServiceClient {

    private final RestTemplate restTemplate;
    private final String productServiceUrl;

    public ProductServiceClient(@Qualifier("internalRestTemplate") RestTemplate restTemplate,
                                 @Value("${cloudmart.services.product-service-url}") String productServiceUrl) {
        this.restTemplate = restTemplate;
        this.productServiceUrl = productServiceUrl;
    }

    public List<ProductDto> listAll() {
        try {
            ProductDto[] products = restTemplate.getForObject(
                    productServiceUrl + "/api/products", ProductDto[].class);
            return products == null ? List.of() : List.of(products);
        } catch (RestClientException ex) {
            return List.of();
        }
    }

    public Optional<ProductDto> getById(Long id) {
        try {
            return Optional.ofNullable(
                    restTemplate.getForObject(productServiceUrl + "/api/products/" + id, ProductDto.class));
        } catch (RestClientException ex) {
            return Optional.empty();
        }
    }

    @Data
    public static class ProductDto {
        private Long id;
        private String name;
        private String description;
        private String category;
        private BigDecimal price;
        private Integer stockQuantity;
    }
}
