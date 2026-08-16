package com.cloudmart.order.service;

import com.cloudmart.order.client.ProductClient;
import com.cloudmart.order.dto.CreateOrderRequest;
import com.cloudmart.order.dto.OrderEvent;
import com.cloudmart.order.kafka.OrderEventProducer;
import com.cloudmart.order.model.Order;
import com.cloudmart.order.model.OrderItem;
import com.cloudmart.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final OrderEventProducer orderEventProducer;

    @Transactional
    public Order placeOrder(CreateOrderRequest request) {
        Order order = Order.builder()
                .userId(request.userId())
                .status(Order.OrderStatus.CREATED)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderRequest.Item item : request.items()) {
            ProductClient.ProductDto product = productClient.getProduct(item.productId());
            if (product == null) {
                throw new IllegalArgumentException("Product not found: " + item.productId());
            }
            productClient.reserveStock(item.productId(), item.quantity());

            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(item.quantity()));
            total = total.add(lineTotal);

            order.addItem(OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(item.quantity())
                    .unitPrice(product.getPrice())
                    .build());
        }
        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);

        orderEventProducer.publishOrderCreated(
                OrderEvent.created(saved.getId(), saved.getUserId(), saved.getTotalAmount()));

        return saved;
    }

    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
    }

    public List<Order> findByUser(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }
}
