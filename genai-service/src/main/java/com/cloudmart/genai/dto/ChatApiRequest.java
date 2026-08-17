package com.cloudmart.genai.dto;

import jakarta.validation.constraints.NotBlank;

// No userId here on purpose - it comes from the gateway-verified X-User-Id
// header (see AssistantController), not from the client. It used to be a
// plain body field, which meant anyone could ask the assistant about a
// different user's orders just by changing a number.
public record ChatApiRequest(
        String conversationId,
        @NotBlank String message
) {}
