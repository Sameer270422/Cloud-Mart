package com.cloudmart.genai.dto;

import com.cloudmart.genai.client.ProductServiceClient;

import java.math.BigDecimal;

public record ProductMatch(
        Long id,
        String name,
        String description,
        String category,
        BigDecimal price,
        Integer stockQuantity
) {
    public static ProductMatch from(ProductServiceClient.ProductDto p) {
        return new ProductMatch(p.getId(), p.getName(), p.getDescription(), p.getCategory(),
                p.getPrice(), p.getStockQuantity());
    }
}
