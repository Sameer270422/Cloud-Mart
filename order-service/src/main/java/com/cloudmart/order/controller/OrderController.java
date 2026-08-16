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
    public ResponseEntity<Order> placeOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(orderService.placeOrder(request));
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable Long id) {
        return orderService.findById(id);
    }

    @GetMapping
    public List<Order> list(@RequestParam(required = false) Long userId) {
        return userId != null ? orderService.findByUser(userId) : orderService.findAll();
    }
}
