package com.cloudmart.order.service;

import com.cloudmart.order.client.ProductClient;
import com.cloudmart.order.dto.CreateOrderRequest;
import com.cloudmart.order.dto.OrderEvent;
import com.cloudmart.order.kafka.OrderEventProducer;
import com.cloudmart.order.model.Order;
import com.cloudmart.order.model.OrderItem;
import com.cloudmart.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final OrderEventProducer orderEventProducer;

    @Transactional
    public Order placeOrder(CreateOrderRequest request, Long userId) {
        Order order = Order.builder()
                .userId(userId)
                .status(Order.OrderStatus.CREATED)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        List<ReservedItem> reserved = new ArrayList<>();
        try {
            for (CreateOrderRequest.Item item : request.items()) {
                ProductClient.ProductDto product = productClient.getProduct(item.productId());
                if (product == null) {
                    throw new IllegalArgumentException("Product not found: " + item.productId());
                }
                productClient.reserveStock(item.productId(), item.quantity());
                reserved.add(new ReservedItem(item.productId(), item.quantity()));

                BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(item.quantity()));
                total = total.add(lineTotal);

                order.addItem(OrderItem.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .quantity(item.quantity())
                        .unitPrice(product.getPrice())
                        .build());
            }
        } catch (RuntimeException ex) {
            releaseReserved(reserved);
            throw ex;
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);

        orderEventProducer.publishOrderCreated(
                OrderEvent.created(saved.getId(), saved.getUserId(), saved.getTotalAmount()));

        return saved;
    }

    // Best-effort rollback of stock already reserved earlier in this order.
    // A release failure is logged, not rethrown - the original failure is
    // what the caller needs to see, and a stuck reservation here is a lesser
    // problem than masking why the order itself failed.
    private void releaseReserved(List<ReservedItem> reserved) {
        for (ReservedItem item : reserved) {
            try {
                productClient.releaseStock(item.productId(), item.quantity());
            } catch (RuntimeException releaseEx) {
                log.error("Failed to release {} units of product {} after order placement failed",
                        item.quantity(), item.productId(), releaseEx);
            }
        }
    }

    private record ReservedItem(Long productId, int quantity) {}

    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
    }

    public List<Order> findByUser(Long userId) {
        return orderRepository.findByUserId(userId);
    }
}
