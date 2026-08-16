package com.cloudmart.product.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProductValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Product.ProductBuilder valid() {
        return Product.builder()
                .name("Widget")
                .description("A widget")
                .category("Test")
                .price(new BigDecimal("9.99"))
                .stockQuantity(10);
    }

    @Test
    void rejectsNegativePrice() {
        Product product = valid().price(new BigDecimal("-1.00")).build();

        Set<ConstraintViolation<Product>> violations = validator.validate(product);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void rejectsZeroPrice() {
        Product product = valid().price(BigDecimal.ZERO).build();

        Set<ConstraintViolation<Product>> violations = validator.validate(product);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void rejectsNegativeStockQuantity() {
        Product product = valid().stockQuantity(-5).build();

        Set<ConstraintViolation<Product>> violations = validator.validate(product);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void rejectsBlankName() {
        Product product = valid().name("  ").build();

        Set<ConstraintViolation<Product>> violations = validator.validate(product);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void acceptsValidProduct() {
        Product product = valid().build();

        Set<ConstraintViolation<Product>> violations = validator.validate(product);

        assertThat(violations).isEmpty();
    }
}
