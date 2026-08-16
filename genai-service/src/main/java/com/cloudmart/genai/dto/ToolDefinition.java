package com.cloudmart.genai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record ToolDefinition(
        String name,
        String description,
        @JsonProperty("input_schema") Map<String, Object> inputSchema
) {}
