package com.cloudmart.product.controller;

import com.cloudmart.product.model.Product;
import com.cloudmart.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

// NOTE: no @CrossOrigin here on purpose - CORS is handled once, at the edge,
// by api-gateway's globalcors config. Adding it here too would make this
// service stamp its own Access-Control-Allow-Origin header onto responses
// the gateway already stamps, producing a response with two values for
// that header, which browsers reject outright (even when both values match).
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<Product> list(@RequestParam(required = false) String category,
                               @RequestParam(required = false) String search) {
        return productService.findAll(category, search);
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable Long id) {
        return productService.findById(id);
    }

    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody Product product,
                                           @RequestHeader("X-User-Role") String role) {
        requireAdmin(role);
        return ResponseEntity.ok(productService.create(product));
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable Long id, @Valid @RequestBody Product product,
                           @RequestHeader("X-User-Role") String role) {
        requireAdmin(role);
        return productService.update(id, product);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @RequestHeader("X-User-Role") String role) {
        requireAdmin(role);
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Catalog mutation is intentionally admin-only - browsing (all GET
    // endpoints) stays public. reserve/release are unaffected: order-service
    // calls those directly over the internal network, not through the
    // gateway, so there's no end-user role to check there.
    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
    }

    @PostMapping("/{id}/reserve")
    public Product reserveStock(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        int quantity = body.getOrDefault("quantity", 1);
        return productService.decrementStock(id, quantity);
    }

    // Compensating endpoint: order-service calls this to undo a reservation
    // (e.g. a later item in the same order failed to reserve).
    @PostMapping("/{id}/release")
    public Product releaseStock(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        int quantity = body.getOrDefault("quantity", 1);
        return productService.incrementStock(id, quantity);
    }
}
