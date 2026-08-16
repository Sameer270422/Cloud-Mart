package com.cloudmart.genai.dto;

import java.util.List;
import java.util.Map;

/**
 * One turn in an Anthropic Messages API conversation. `content` is a list
 * of heterogeneous content blocks (text / tool_use / tool_result) - kept as
 * raw maps rather than a sealed hierarchy since we only ever pass them
 * through to/from the API rather than deeply manipulating them in Java.
 */
public record ChatMessage(String role, List<Map<String, Object>> content) {

    public static ChatMessage user(List<Map<String, Object>> content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(List<Map<String, Object>> content) {
        return new ChatMessage("assistant", content);
    }
}
