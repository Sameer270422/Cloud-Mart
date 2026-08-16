package com.cloudmart.genai.dto;

import java.util.List;
import java.util.Map;

public final class ContentBlocks {

    private ContentBlocks() {}

    public static Map<String, Object> text(String text) {
        return Map.of("type", "text", "text", text);
    }

    public static Map<String, Object> toolResult(String toolUseId, String content) {
        return Map.of("type", "tool_result", "tool_use_id", toolUseId, "content", content);
    }

    public static boolean isToolUse(Map<String, Object> block) {
        return "tool_use".equals(block.get("type"));
    }

    public static String extractText(List<Map<String, Object>> content) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> block : content) {
            if ("text".equals(block.get("type"))) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(block.get("text"));
            }
        }
        return sb.toString();
    }
}
