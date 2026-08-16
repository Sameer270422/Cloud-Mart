package com.cloudmart.order.service;

import com.cloudmart.order.client.ProductClient;
import com.cloudmart.order.dto.CreateOrderRequest;
import com.cloudmart.order.kafka.OrderEventProducer;
import com.cloudmart.order.model.Order;
import com.cloudmart.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private OrderEventProducer orderEventProducer;

    private OrderService orderService() {
        return new OrderService(orderRepository, productClient, orderEventProducer);
    }

    @Test
    void releasesEarlierReservationsWhenALaterItemFailsToReserve() {
        var item1 = new CreateOrderRequest.Item(1L, 2);
        var item2 = new CreateOrderRequest.Item(2L, 1);
        var request = new CreateOrderRequest(42L, List.of(item1, item2));

        var product1 = new ProductClient.ProductDto();
        product1.setId(1L);
        product1.setName("Widget");
        product1.setPrice(new BigDecimal("10.00"));

        when(productClient.getProduct(1L)).thenReturn(product1);
        when(productClient.getProduct(2L)).thenThrow(new IllegalArgumentException("Product not found: 2"));

        OrderService service = orderService();

        assertThatThrownBy(() -> service.placeOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found");

        verify(productClient).reserveStock(1L, 2);
        verify(productClient).releaseStock(1L, 2);
        verify(productClient, never()).reserveStock(eq(2L), anyInt());
        verifyNoInteractions(orderRepository, orderEventProducer);
    }

    @Test
    void releaseFailureDoesNotMaskTheOriginalOrderFailure() {
        var item1 = new CreateOrderRequest.Item(1L, 2);
        var item2 = new CreateOrderRequest.Item(2L, 1);
        var request = new CreateOrderRequest(42L, List.of(item1, item2));

        var product1 = new ProductClient.ProductDto();
        product1.setId(1L);
        product1.setName("Widget");
        product1.setPrice(new BigDecimal("10.00"));

        when(productClient.getProduct(1L)).thenReturn(product1);
        when(productClient.getProduct(2L)).thenThrow(new IllegalArgumentException("Product not found: 2"));
        doThrow(new RuntimeException("product-service unreachable"))
                .when(productClient).releaseStock(1L, 2);

        assertThatThrownBy(() -> orderService().placeOrder(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    void doesNotReleaseAnythingWhenTheWholeOrderSucceeds() {
        var item1 = new CreateOrderRequest.Item(1L, 2);
        var request = new CreateOrderRequest(42L, List.of(item1));

        var product1 = new ProductClient.ProductDto();
        product1.setId(1L);
        product1.setName("Widget");
        product1.setPrice(new BigDecimal("10.00"));

        when(productClient.getProduct(1L)).thenReturn(product1);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(99L);
            return o;
        });

        orderService().placeOrder(request);

        verify(productClient).reserveStock(1L, 2);
        verify(productClient, never()).releaseStock(anyLong(), anyInt());
        verify(orderEventProducer).publishOrderCreated(any());
    }
}
