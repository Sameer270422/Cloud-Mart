package com.cloudmart.genai.client;

import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class OrderServiceClient {

    private final RestTemplate restTemplate;
    private final String orderServiceUrl;

    public OrderServiceClient(@Qualifier("internalRestTemplate") RestTemplate restTemplate,
                               @Value("${cloudmart.services.order-service-url}") String orderServiceUrl) {
        this.restTemplate = restTemplate;
        this.orderServiceUrl = orderServiceUrl;
    }

    /**
     * order-service itself doesn't scope orders by caller identity (no
     * gateway-level auth yet), so the assistant enforces it here: an order
     * that exists but belongs to a different user is treated exactly like
     * a missing one, rather than exposing its details through the chat.
     */
    public Optional<OrderDto> getOrderForUser(Long orderId, Long userId) {
        try {
            OrderDto order = restTemplate.getForObject(orderServiceUrl + "/api/orders/" + orderId, OrderDto.class);
            if (order == null || !userId.equals(order.getUserId())) {
                return Optional.empty();
            }
            return Optional.of(order);
        } catch (RestClientException ex) {
            return Optional.empty();
        }
    }

    public List<OrderDto> listByUser(Long userId) {
        try {
            OrderDto[] orders = restTemplate.getForObject(
                    orderServiceUrl + "/api/orders?userId=" + userId, OrderDto[].class);
            return orders == null ? List.of() : List.of(orders);
        } catch (RestClientException ex) {
            return List.of();
        }
    }

    @Data
    public static class OrderDto {
        private Long id;
        private Long userId;
        private String status;
        private BigDecimal totalAmount;
        private Instant createdAt;
        private List<OrderItemDto> items;
    }

    @Data
    public static class OrderItemDto {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
