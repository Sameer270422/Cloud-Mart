package com.cloudmart.genai.dto;

import java.math.BigDecimal;

/**
 * Signals to the frontend that this product/quantity should be added to the
 * cart. The cart itself lives client-side (browser state, no server-side
 * cart), so this isn't a mutation genai-service performs directly - it's an
 * instruction the chat widget acts on using the exact same addItem() path
 * the "Add to cart" button already calls.
 */
public record CartAddition(Long id, String name, BigDecimal price, Integer quantity) {}
