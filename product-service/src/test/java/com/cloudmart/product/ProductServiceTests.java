package com.cloudmart.product;

import com.cloudmart.product.model.Product;
import com.cloudmart.product.repository.ProductRepository;
import com.cloudmart.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:producttestdb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class ProductServiceTests {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    private Long productId;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        Product product = Product.builder()
                .name("Test Widget")
                .description("A widget for testing")
                .category("Test")
                .price(new BigDecimal("9.99"))
                .stockQuantity(10)
                .build();
        productId = productService.create(product).getId();
    }

    @Test
    void decrementStockReducesQuantity() {
        Product updated = productService.decrementStock(productId, 3);
        assertThat(updated.getStockQuantity()).isEqualTo(7);
    }

    @Test
    void decrementStockThrowsWhenInsufficient() {
        assertThrows(IllegalArgumentException.class, () -> productService.decrementStock(productId, 100));
    }
}
