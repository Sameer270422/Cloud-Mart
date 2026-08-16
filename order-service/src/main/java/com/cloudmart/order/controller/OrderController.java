package com.cloudmart.order.controller;

import com.cloudmart.order.dto.CreateOrderRequest;
import com.cloudmart.order.model.Order;
import com.cloudmart.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// NOTE: no @CrossOrigin here on purpose - see ProductController for why
// (CORS is handled once, centrally, by api-gateway).
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<Order> placeOrder(@Valid @RequestBody CreateOrderRequest request,
                                             @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(orderService.placeOrder(request, userId));
    }

    // Same "Order not found" response (400, via the existing
    // IllegalArgumentException handler) for a mismatch as for a genuinely
    // nonexistent id - not 403 - so this doesn't confirm to a caller that an
    // order id exists at all when it isn't theirs.
    @GetMapping("/{id}")
    public Order get(@PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        Order order = orderService.findById(id);
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Order not found: " + id);
        }
        return order;
    }

    // Always scoped to the caller - there used to be a no-param branch here
    // that returned every order in the system to anyone who asked.
    @GetMapping
    public List<Order> list(@RequestHeader("X-User-Id") Long userId) {
        return orderService.findByUser(userId);
    }
}
