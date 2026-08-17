package com.cloudmart.genai.service;

import com.cloudmart.genai.client.AnthropicClient;
import com.cloudmart.genai.client.OrderServiceClient;
import com.cloudmart.genai.client.ProductServiceClient;
import com.cloudmart.genai.dto.CartAddition;
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
            products, check on their orders, and add items to their cart.

            Always use the search_products tool to look up products - never
            answer from memory or guess at what's in the catalog. Use
            get_order_status for a specific order number, or list_my_orders
            if the customer just wants to know what they've ordered.

            When the customer asks to add, order, or buy something, use
            add_to_cart. If they already saw search results earlier in this
            conversation, use the product id from those results directly -
            don't search again just to re-find something already found. If
            it's ambiguous which product they mean, ask instead of guessing.

            You cannot see what's currently in the customer's cart - it lives
            in their browser, not here. When they ask to check out or place
            their order, don't call place_order right away: first ask them
            to confirm they're ready to complete the purchase. Only call
            place_order after they've explicitly confirmed (e.g. "yes",
            "go ahead", "place it"). If they haven't added anything to their
            cart yet, tell them to add something first.

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
                    "add_to_cart",
                    "Add a product to the customer's cart. Use the product id from an earlier search_products result.",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "productId", Map.of("type", "integer", "description", "The id of the product to add"),
                                    "quantity", Map.of("type", "integer", "description", "How many to add (default 1)")
                            ),
                            "required", List.of("productId"))),
            new ToolDefinition(
                    "place_order",
                    "Check out and place an order for whatever is currently in the customer's cart. Only call this after the customer has explicitly confirmed they want to complete the purchase.",
                    Map.of("type", "object", "properties", Map.of())),
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
    private final ProductServiceClient productServiceClient;
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
        List<CartAddition> cartAdditions = new ArrayList<>();
        boolean checkoutRequested = false;

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            MessagesResponse response = anthropicClient.sendMessage(SYSTEM_PROMPT, history, TOOLS);
            history.add(ChatMessage.assistant(response.content()));

            List<Map<String, Object>> toolUses = response.content().stream()
                    .filter(ContentBlocks::isToolUse)
                    .toList();

            if (toolUses.isEmpty() || !"tool_use".equals(response.stopReason())) {
                trimHistory(history);
                return new ChatResult(convId, ContentBlocks.extractText(response.content()),
                        lastSearchResults, cartAdditions, checkoutRequested);
            }

            List<Map<String, Object>> toolResults = new ArrayList<>();
            for (Map<String, Object> toolUse : toolUses) {
                ToolExecution execution = executeTool(toolUse, userId);
                if (execution.searchResults() != null) {
                    lastSearchResults = execution.searchResults();
                }
                if (execution.cartAddition() != null) {
                    cartAdditions.add(execution.cartAddition());
                }
                if (execution.checkoutRequested()) {
                    checkoutRequested = true;
                }
                toolResults.add(ContentBlocks.toolResult((String) toolUse.get("id"), execution.resultJson()));
            }
            history.add(ChatMessage.user(toolResults));
        }

        log.warn("Tool loop exceeded {} iterations for conversation {}", MAX_TOOL_ITERATIONS, convId);
        trimHistory(history);
        return new ChatResult(convId, "Sorry, I'm having trouble finishing that up right now - could you try again?",
                lastSearchResults, cartAdditions, checkoutRequested);
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
                    yield new ToolExecution(objectMapper.writeValueAsString(matches), matches, null, false);
                }
                case "add_to_cart" -> executeAddToCart(input);
                case "place_order" -> new ToolExecution(
                        "Checkout has been handed off to the customer's app to complete using their current cart.",
                        null, null, true);
                case "get_order_status" -> {
                    long orderId = ((Number) input.get("orderId")).longValue();
                    String result = orderServiceClient.getOrderForUser(orderId, userId)
                            .map(this::writeQuietly)
                            .orElse("No order with that number was found for this customer.");
                    yield new ToolExecution(result, null, null, false);
                }
                case "list_my_orders" -> {
                    var orders = orderServiceClient.listByUser(userId);
                    yield new ToolExecution(objectMapper.writeValueAsString(orders), null, null, false);
                }
                default -> new ToolExecution("Unknown tool: " + toolName, null, null, false);
            };
        } catch (Exception ex) {
            log.error("Tool execution failed for {}", toolName, ex);
            return new ToolExecution("That lookup failed - please try again.", null, null, false);
        }
    }

    private ToolExecution executeAddToCart(Map<String, Object> input) {
        long productId = ((Number) input.get("productId")).longValue();
        int requestedQuantity = input.get("quantity") instanceof Number n ? n.intValue() : 1;

        var product = productServiceClient.getById(productId);
        if (product.isEmpty()) {
            return new ToolExecution("No product with id " + productId + " exists.", null, null, false);
        }
        if (requestedQuantity <= 0) {
            return new ToolExecution("Quantity must be at least 1.", null, null, false);
        }

        var p = product.get();
        int available = p.getStockQuantity() == null ? 0 : p.getStockQuantity();
        if (available <= 0) {
            return new ToolExecution(p.getName() + " is currently out of stock.", null, null, false);
        }

        int actualQuantity = Math.min(requestedQuantity, available);
        var addition = new CartAddition(p.getId(), p.getName(), p.getPrice(), actualQuantity);

        String note = actualQuantity < requestedQuantity
                ? " (only %d in stock, so I added %d instead of %d)".formatted(available, actualQuantity, requestedQuantity)
                : "";
        String result = "Added %dx %s ($%s each) to the cart.%s".formatted(actualQuantity, p.getName(), p.getPrice(), note);
        return new ToolExecution(result, null, addition, false);
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

    private record ToolExecution(String resultJson, List<ProductMatch> searchResults, CartAddition cartAddition,
                                  boolean checkoutRequested) {}

    public record ChatResult(String conversationId, String reply, List<ProductMatch> productCards,
                              List<CartAddition> cartAdditions, boolean checkoutRequested) {}
}
