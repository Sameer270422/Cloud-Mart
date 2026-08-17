package com.cloudmart.genai.dto;

import java.util.List;

public record ChatApiResponse(
        String conversationId,
        String reply,
        List<ProductMatch> productCards,
        List<CartAddition> cartAdditions,
        boolean checkoutRequested
) {}
