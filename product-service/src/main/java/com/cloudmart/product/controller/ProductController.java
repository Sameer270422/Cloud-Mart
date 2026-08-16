package com.cloudmart.product.controller;

import com.cloudmart.product.model.Product;
import com.cloudmart.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<Product> create(@Valid @RequestBody Product product) {
        return ResponseEntity.ok(productService.create(product));
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable Long id, @Valid @RequestBody Product product) {
        return productService.update(id, product);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reserve")
    public Product reserveStock(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        int quantity = body.getOrDefault("quantity", 1);
        return productService.decrementStock(id, quantity);
    }
}
