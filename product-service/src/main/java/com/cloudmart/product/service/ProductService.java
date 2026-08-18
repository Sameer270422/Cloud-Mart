package com.cloudmart.product.service;

import com.cloudmart.product.dto.CategoryNode;
import com.cloudmart.product.model.Product;
import com.cloudmart.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public List<Product> findAll(String category, String subcategory, String search) {
        if (category != null && !category.isBlank() && subcategory != null && !subcategory.isBlank()) {
            return productRepository.findByCategoryIgnoreCaseAndSubcategoryIgnoreCase(category, subcategory);
        }
        if (category != null && !category.isBlank()) {
            return productRepository.findByCategoryIgnoreCase(category);
        }
        if (search != null && !search.isBlank()) {
            return productRepository.findByNameContainingIgnoreCase(search);
        }
        return productRepository.findAll();
    }

    // Derived from the live catalog rather than a separately-managed table -
    // for a catalog this size there's no real category-management workflow
    // to speak of, and deriving it means the sidebar can never drift out of
    // sync with what's actually in stock.
    public List<CategoryNode> getCategoryTree() {
        var byCategory = new LinkedHashMap<String, TreeSet<String>>();
        for (Product p : productRepository.findAll()) {
            if (p.getCategory() == null || p.getCategory().isBlank()) {
                continue;
            }
            var subcategories = byCategory.computeIfAbsent(p.getCategory(), c -> new TreeSet<>());
            if (p.getSubcategory() != null && !p.getSubcategory().isBlank()) {
                subcategories.add(p.getSubcategory());
            }
        }
        return byCategory.entrySet().stream()
                .sorted(Comparator.comparing(java.util.Map.Entry::getKey))
                .map(e -> new CategoryNode(e.getKey(), List.copyOf(e.getValue())))
                .toList();
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
    }

    public Product create(Product product) {
        return productRepository.save(product);
    }

    public Product update(Long id, Product updated) {
        Product existing = findById(id);
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setCategory(updated.getCategory());
        existing.setSubcategory(updated.getSubcategory());
        existing.setPrice(updated.getPrice());
        existing.setStockQuantity(updated.getStockQuantity());
        return productRepository.save(existing);
    }

    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    @Transactional
    public Product decrementStock(Long id, int quantity) {
        Product product = findById(id);
        if (product.getStockQuantity() < quantity) {
            throw new IllegalArgumentException("Insufficient stock for product: " + product.getName());
        }
        product.setStockQuantity(product.getStockQuantity() - quantity);
        return productRepository.save(product);
    }

    /**
     * Compensating action for decrementStock, used by order-service to release
     * already-reserved stock when a later item in the same order fails to reserve.
     */
    @Transactional
    public Product incrementStock(Long id, int quantity) {
        Product product = findById(id);
        product.setStockQuantity(product.getStockQuantity() + quantity);
        return productRepository.save(product);
    }
}
