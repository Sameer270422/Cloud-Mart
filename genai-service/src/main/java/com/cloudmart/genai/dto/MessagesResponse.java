package com.cloudmart.genai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MessagesResponse(
        String id,
        String role,
        List<Map<String, Object>> content,
        @JsonProperty("stop_reason") String stopReason
) {}
