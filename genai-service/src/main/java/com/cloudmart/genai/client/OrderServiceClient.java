package com.cloudmart.genai.client;

import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

    // order-service's order endpoints require X-User-Id (it trusts the
    // header unconditionally, same as every other service behind the
    // gateway) - genai-service asserts the identity it was itself handed
    // by the gateway on the inbound request, since that's exactly who
    // these lookups are being made on behalf of.
    private HttpEntity<Void> withUserId(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        return new HttpEntity<>(headers);
    }

    /**
     * Belt-and-suspenders: order-service already scopes GET /api/orders/{id}
     * to the caller via X-User-Id (a mismatched order comes back as a 404),
     * but the userId equality check stays here too in case that ever
     * changes - the assistant should never expose another user's order
     * regardless of what the header-based check does upstream.
     */
    public Optional<OrderDto> getOrderForUser(Long orderId, Long userId) {
        try {
            OrderDto order = restTemplate.exchange(orderServiceUrl + "/api/orders/" + orderId,
                    HttpMethod.GET, withUserId(userId), OrderDto.class).getBody();
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
            OrderDto[] orders = restTemplate.exchange(orderServiceUrl + "/api/orders",
                    HttpMethod.GET, withUserId(userId), OrderDto[].class).getBody();
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
