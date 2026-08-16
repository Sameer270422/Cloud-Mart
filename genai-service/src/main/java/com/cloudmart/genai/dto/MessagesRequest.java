package com.cloudmart.genai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MessagesRequest(
        String model,
        @JsonProperty("max_tokens") int maxTokens,
        String system,
        List<ChatMessage> messages,
        List<ToolDefinition> tools
) {}
