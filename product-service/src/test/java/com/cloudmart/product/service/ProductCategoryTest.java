package com.cloudmart.product.service;

import com.cloudmart.product.model.Product;
import com.cloudmart.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:productcategorytestdb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class ProductCategoryTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        save("Keyboard", "Electronics", "Keyboards");
        save("Monitor", "Electronics", "Monitors");
        save("Headphones", "Electronics", "Audio");
        save("Office Chair", "Furniture", "Chairs");
        save("Unsorted Widget", "Furniture", null);
    }

    private void save(String name, String category, String subcategory) {
        productService.create(Product.builder()
                .name(name)
                .description("desc")
                .category(category)
                .subcategory(subcategory)
                .price(new BigDecimal("9.99"))
                .stockQuantity(10)
                .build());
    }

    @Test
    void groupsSubcategoriesUnderTheirCategorySortedAndDeduped() {
        var tree = productService.getCategoryTree();

        var electronics = tree.stream().filter(n -> n.category().equals("Electronics")).findFirst().orElseThrow();
        assertThat(electronics.subcategories()).containsExactly("Audio", "Keyboards", "Monitors");
    }

    @Test
    void categoryWithNoSubcategorizedProductsStillAppearsWithAnEmptyList() {
        save("Second Unsorted Widget", "Furniture", null);

        var tree = productService.getCategoryTree();
        var furniture = tree.stream().filter(n -> n.category().equals("Furniture")).findFirst().orElseThrow();

        assertThat(furniture.subcategories()).containsExactly("Chairs");
    }

    @Test
    void findAllFiltersByCategoryAndSubcategoryTogether() {
        var results = productService.findAll("Electronics", "Audio", null);

        assertThat(results).extracting(Product::getName).containsExactly("Headphones");
    }

    @Test
    void findAllFallsBackToCategoryOnlyWhenSubcategoryIsBlank() {
        var results = productService.findAll("Electronics", "", null);

        assertThat(results).extracting(Product::getName).containsExactlyInAnyOrder("Keyboard", "Monitor", "Headphones");
    }
}
