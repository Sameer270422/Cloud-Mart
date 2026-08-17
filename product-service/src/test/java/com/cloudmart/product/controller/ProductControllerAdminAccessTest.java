package com.cloudmart.product.controller;

import com.cloudmart.product.model.Product;
import com.cloudmart.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerAdminAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    private Product aProduct() {
        return Product.builder()
                .id(1L)
                .name("Widget")
                .description("A widget")
                .category("Test")
                .price(new BigDecimal("9.99"))
                .stockQuantity(10)
                .build();
    }

    @Test
    void createRejectsARequestWithNoRoleHeader() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(aProduct())))
                .andExpect(status().isBadRequest()); // missing required header
    }

    @Test
    void createRejectsANonAdminRole() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("X-User-Role", "CUSTOMER")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(aProduct())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createSucceedsForAnAdmin() throws Exception {
        when(productService.create(any(Product.class))).thenReturn(aProduct());

        mockMvc.perform(post("/api/products")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(aProduct())))
                .andExpect(status().isOk());
    }

    @Test
    void deleteRejectsANonAdminRole() throws Exception {
        mockMvc.perform(delete("/api/products/1").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listProductsRequiresNoRoleAtAll() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());
    }
}
