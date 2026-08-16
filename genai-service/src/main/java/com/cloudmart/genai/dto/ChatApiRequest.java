package com.cloudmart.genai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatApiRequest(
        String conversationId,
        @NotNull Long userId,
        @NotBlank String message
) {}
