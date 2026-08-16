package com.cloudmart.genai.service;

import com.cloudmart.genai.client.AnthropicClient;
import com.cloudmart.genai.client.OrderServiceClient;
import com.cloudmart.genai.dto.ChatMessage;
import com.cloudmart.genai.dto.ContentBlocks;
import com.cloudmart.genai.dto.MessagesResponse;
import com.cloudmart.genai.dto.ProductMatch;
import com.cloudmart.genai.dto.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the multi-turn tool-use loop against Claude: send the conversation
 * so far, execute whatever tools it asks for, feed the results back, repeat
 * until it produces a final answer. Conversation state is kept in memory
 * per conversationId - fine for a single-instance demo; a real deployment
 * would move this to Redis/a database so it survives restarts and works
 * across replicas (same story as notification-service's old in-memory
 * store).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantChatService {

    private static final int MAX_TOOL_ITERATIONS = 5;
    private static final int MAX_HISTORY_MESSAGES = 20;

    private static final String SYSTEM_PROMPT = """
            You are CloudMart's shopping assistant. Help customers find
            products and check on their orders.

            Always use the search_products tool to look up products - never
            answer from memory or guess at what's in the catalog. Use
            get_order_status for a specific order number, or list_my_orders
            if the customer just wants to know what they've ordered.

            Keep replies short, friendly, and concrete. If a tool returns no
            results, say so plainly rather than inventing an answer.
            """;

    private static final List<ToolDefinition> TOOLS = List.of(
            new ToolDefinition(
                    "search_products",
                    "Semantically search the CloudMart product catalog. Use this for any question about what products are available, even if the customer's wording doesn't match product names exactly.",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "query", Map.of("type", "string", "description", "What the customer is looking for, in their own words"),
                                    "maxResults", Map.of("type", "integer", "description", "Maximum number of products to return (default 5)")
                            ),
                            "required", List.of("query"))),
            new ToolDefinition(
                    "get_order_status",
                    "Look up a single order by its order number for the current customer.",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "orderId", Map.of("type", "integer", "description", "The order number")
                            ),
                            "required", List.of("orderId"))),
            new ToolDefinition(
                    "list_my_orders",
                    "List all orders placed by the current customer.",
                    Map.of("type", "object", "properties", Map.of())));

    private final AnthropicClient anthropicClient;
    private final SemanticSearchService semanticSearchService;
    private final OrderServiceClient orderServiceClient;
    private final ObjectMapper objectMapper;

    private final Map<String, List<ChatMessage>> conversations = new ConcurrentHashMap<>();

    public ChatResult chat(String conversationId, Long userId, String userMessage) {
        String convId = conversationId != null && conversations.containsKey(conversationId)
                ? conversationId
                : UUID.randomUUID().toString();
        List<ChatMessage> history = conversations.computeIfAbsent(convId, id -> new ArrayList<>());

        history.add(ChatMessage.user(List.of(ContentBlocks.text(userMessage))));

        List<ProductMatch> lastSearchResults = List.of();

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            MessagesResponse response = anthropicClient.sendMessage(SYSTEM_PROMPT, history, TOOLS);
            history.add(ChatMessage.assistant(response.content()));

            List<Map<String, Object>> toolUses = response.content().stream()
                    .filter(ContentBlocks::isToolUse)
                    .toList();

            if (toolUses.isEmpty() || !"tool_use".equals(response.stopReason())) {
                trimHistory(history);
                return new ChatResult(convId, ContentBlocks.extractText(response.content()), lastSearchResults);
            }

            List<Map<String, Object>> toolResults = new ArrayList<>();
            for (Map<String, Object> toolUse : toolUses) {
                ToolExecution execution = executeTool(toolUse, userId);
                if (execution.searchResults() != null) {
                    lastSearchResults = execution.searchResults();
                }
                toolResults.add(ContentBlocks.toolResult((String) toolUse.get("id"), execution.resultJson()));
            }
            history.add(ChatMessage.user(toolResults));
        }

        log.warn("Tool loop exceeded {} iterations for conversation {}", MAX_TOOL_ITERATIONS, convId);
        trimHistory(history);
        return new ChatResult(convId, "Sorry, I'm having trouble finishing that up right now - could you try again?", lastSearchResults);
    }

    @SuppressWarnings("unchecked")
    private ToolExecution executeTool(Map<String, Object> toolUse, Long userId) {
        String toolName = (String) toolUse.get("name");
        Map<String, Object> input = (Map<String, Object>) toolUse.getOrDefault("input", Map.of());

        try {
            return switch (toolName) {
                case "search_products" -> {
                    String query = (String) input.get("query");
                    int maxResults = input.get("maxResults") instanceof Number n ? n.intValue() : 5;
                    List<ProductMatch> matches = semanticSearchService.search(query, maxResults);
                    yield new ToolExecution(objectMapper.writeValueAsString(matches), matches);
                }
                case "get_order_status" -> {
                    long orderId = ((Number) input.get("orderId")).longValue();
                    String result = orderServiceClient.getOrderForUser(orderId, userId)
                            .map(this::writeQuietly)
                            .orElse("No order with that number was found for this customer.");
                    yield new ToolExecution(result, null);
                }
                case "list_my_orders" -> {
                    var orders = orderServiceClient.listByUser(userId);
                    yield new ToolExecution(objectMapper.writeValueAsString(orders), null);
                }
                default -> new ToolExecution("Unknown tool: " + toolName, null);
            };
        } catch (Exception ex) {
            log.error("Tool execution failed for {}", toolName, ex);
            return new ToolExecution("That lookup failed - please try again.", null);
        }
    }

    private String writeQuietly(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "Unable to format order details.";
        }
    }

    // Bounds token usage (and cost) for long-running conversations by
    // dropping the oldest turns once history grows past the cap.
    private void trimHistory(List<ChatMessage> history) {
        while (history.size() > MAX_HISTORY_MESSAGES) {
            history.remove(0);
        }
    }

    private record ToolExecution(String resultJson, List<ProductMatch> searchResults) {}

    public record ChatResult(String conversationId, String reply, List<ProductMatch> productCards) {}
}
