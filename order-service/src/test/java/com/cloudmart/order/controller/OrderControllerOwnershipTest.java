package com.cloudmart.order.controller;

import com.cloudmart.order.dto.CreateOrderRequest;
import com.cloudmart.order.model.Order;
import com.cloudmart.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerOwnershipTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private Order orderOwnedBy(Long userId) {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(userId);
        order.setTotalAmount(BigDecimal.TEN);
        return order;
    }

    @Test
    void getReturnsTheOrderWhenTheCallerOwnsIt() throws Exception {
        when(orderService.findById(1L)).thenReturn(orderOwnedBy(7L));

        mockMvc.perform(get("/api/orders/1").header("X-User-Id", "7"))
                .andExpect(status().isOk());
    }

    @Test
    void getReturns400NotTheOrderWhenTheCallerDoesNotOwnIt() throws Exception {
        when(orderService.findById(1L)).thenReturn(orderOwnedBy(7L));

        mockMvc.perform(get("/api/orders/1").header("X-User-Id", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Order not found: 1"));
    }

    @Test
    void listIsAlwaysScopedToTheCallingUserRegardlessOfAnyOtherInput() throws Exception {
        when(orderService.findByUser(7L)).thenReturn(List.of(orderOwnedBy(7L)));

        mockMvc.perform(get("/api/orders").header("X-User-Id", "7"))
                .andExpect(status().isOk());

        verify(orderService).findByUser(7L);
    }

    @Test
    void listRequiresTheUserIdHeader() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeOrderUsesTheHeaderUserIdNotAnythingFromTheBody() throws Exception {
        var request = new CreateOrderRequest(List.of(new CreateOrderRequest.Item(1L, 2)));
        when(orderService.placeOrder(any(), eq(7L))).thenReturn(orderOwnedBy(7L));

        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", "7")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(orderService).placeOrder(any(), eq(7L));
    }
}
