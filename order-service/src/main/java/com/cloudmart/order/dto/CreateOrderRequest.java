package com.cloudmart.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

// No userId field here on purpose - who's placing the order comes from the
// gateway-verified X-User-Id header (see OrderController), not from
// something the client could set to any value it likes.
public record CreateOrderRequest(
        @NotEmpty @Valid List<Item> items
) {
    public record Item(
            @NotNull Long productId,
            @NotNull @Positive Integer quantity
    ) {}
}
