package com.cloudmart.genai.service;

import com.cloudmart.genai.client.OrderServiceClient;
import com.cloudmart.genai.client.ProductServiceClient;
import com.cloudmart.genai.dto.ContentBlocks;
import com.cloudmart.genai.dto.MessagesResponse;
import com.cloudmart.genai.dto.ProductMatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssistantChatServiceTest {

    @Mock
    private com.cloudmart.genai.client.AnthropicClient anthropicClient;

    @Mock
    private SemanticSearchService semanticSearchService;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private OrderServiceClient orderServiceClient;

    private AssistantChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new AssistantChatService(anthropicClient, semanticSearchService, productServiceClient, orderServiceClient, new ObjectMapper());
    }

    private MessagesResponse toolUseResponse(String toolName, Map<String, Object> input) {
        Map<String, Object> block = Map.of(
                "type", "tool_use", "id", "toolu_1", "name", toolName, "input", input);
        return new MessagesResponse("id", "assistant", List.of(block), "tool_use");
    }

    private MessagesResponse finalTextResponse(String text) {
        return new MessagesResponse("id", "assistant", List.of(ContentBlocks.text(text)), "end_turn");
    }

    private ProductServiceClient.ProductDto product(long id, String name, int stock) {
        var p = new ProductServiceClient.ProductDto();
        p.setId(id);
        p.setName(name);
        p.setPrice(new BigDecimal("19.99"));
        p.setStockQuantity(stock);
        return p;
    }

    @Test
    void answersDirectlyWithoutToolsWhenNoneAreNeeded() {
        when(anthropicClient.sendMessage(any(), any(), any())).thenReturn(finalTextResponse("Hi, how can I help?"));

        var result = chatService.chat(null, 1L, "hello");

        assertThat(result.reply()).isEqualTo("Hi, how can I help?");
        assertThat(result.productCards()).isEmpty();
        assertThat(result.cartAdditions()).isEmpty();
        verifyNoInteractions(semanticSearchService, orderServiceClient, productServiceClient);
    }

    @Test
    void callsSearchProductsToolAndReturnsProductCards() {
        var matches = List.of(new ProductMatch(1L, "Keyboard", "desc", "Electronics", new BigDecimal("89.99"), 10));
        when(semanticSearchService.search("mechanical keyboard", 5)).thenReturn(matches);
        when(anthropicClient.sendMessage(any(), any(), any()))
                .thenReturn(toolUseResponse("search_products", Map.of("query", "mechanical keyboard", "maxResults", 5)))
                .thenReturn(finalTextResponse("I found a great keyboard for you."));

        var result = chatService.chat(null, 1L, "I need a mechanical keyboard");

        assertThat(result.reply()).isEqualTo("I found a great keyboard for you.");
        assertThat(result.productCards()).extracting(ProductMatch::id).containsExactly(1L);
        verify(semanticSearchService).search("mechanical keyboard", 5);
    }

    @Test
    void scopesOrderLookupsToTheRequestingUser() {
        when(orderServiceClient.getOrderForUser(42L, 7L)).thenReturn(Optional.empty());
        when(anthropicClient.sendMessage(any(), any(), any()))
                .thenReturn(toolUseResponse("get_order_status", Map.of("orderId", 42)))
                .thenReturn(finalTextResponse("I couldn't find that order."));

        var result = chatService.chat(null, 7L, "where's order 42?");

        assertThat(result.reply()).isEqualTo("I couldn't find that order.");
        verify(orderServiceClient).getOrderForUser(42L, 7L);
    }

    @Test
    void continuesConversationHistoryAcrossTurnsWithTheSameConversationId() {
        when(anthropicClient.sendMessage(any(), any(), any())).thenReturn(finalTextResponse("ok"));

        var first = chatService.chat(null, 1L, "hello");
        chatService.chat(first.conversationId(), 1L, "follow up");

        verify(anthropicClient, times(2)).sendMessage(any(), any(), any());
    }

    @Test
    void stopsAfterMaxIterationsAndReturnsAFallbackReply() {
        when(anthropicClient.sendMessage(any(), any(), any()))
                .thenReturn(toolUseResponse("search_products", Map.of("query", "x")));
        when(semanticSearchService.search(anyString(), anyInt())).thenReturn(List.of());

        var result = chatService.chat(null, 1L, "loop forever");

        assertThat(result.reply()).containsIgnoringCase("trouble");
    }

    @Test
    void addToCartResolvesTheProductAndReturnsACartAddition() {
        when(productServiceClient.getById(6L)).thenReturn(Optional.of(product(6L, "Keyboard", 10)));
        when(anthropicClient.sendMessage(any(), any(), any()))
                .thenReturn(toolUseResponse("add_to_cart", Map.of("productId", 6, "quantity", 2)))
                .thenReturn(finalTextResponse("Added 2 keyboards to your cart."));

        var result = chatService.chat(null, 1L, "add 2 of that keyboard to my cart");

        assertThat(result.cartAdditions()).hasSize(1);
        var addition = result.cartAdditions().get(0);
        assertThat(addition.id()).isEqualTo(6L);
        assertThat(addition.name()).isEqualTo("Keyboard");
        assertThat(addition.quantity()).isEqualTo(2);
    }

    @Test
    void addToCartDefaultsQuantityToOneWhenNotSpecified() {
        when(productServiceClient.getById(6L)).thenReturn(Optional.of(product(6L, "Keyboard", 10)));
        when(anthropicClient.sendMessage(any(), any(), any()))
                .thenReturn(toolUseResponse("add_to_cart", Map.of("productId", 6)))
                .thenReturn(finalTextResponse("Added it."));

        var result = chatService.chat(null, 1L, "add that keyboard");

        assertThat(result.cartAdditions().get(0).quantity()).isEqualTo(1);
    }

    @Test
    void addToCartCapsQuantityToAvailableStock() {
        when(productServiceClient.getById(6L)).thenReturn(Optional.of(product(6L, "Keyboard", 3)));
        when(anthropicClient.sendMessage(any(), any(), any()))
                .thenReturn(toolUseResponse("add_to_cart", Map.of("productId", 6, "quantity", 50)))
                .thenReturn(finalTextResponse("Added what I could."));

        var result = chatService.chat(null, 1L, "add 50 keyboards");

        assertThat(result.cartAdditions().get(0).quantity()).isEqualTo(3);
    }

    @Test
    void addToCartAddsNothingWhenProductIsOutOfStock() {
        when(productServiceClient.getById(6L)).thenReturn(Optional.of(product(6L, "Keyboard", 0)));
        when(anthropicClient.sendMessage(any(), any(), any()))
                .thenReturn(toolUseResponse("add_to_cart", Map.of("productId", 6, "quantity", 1)))
                .thenReturn(finalTextResponse("That's out of stock right now."));

        var result = chatService.chat(null, 1L, "add that keyboard");

        assertThat(result.cartAdditions()).isEmpty();
    }

    @Test
    void addToCartAddsNothingWhenProductDoesNotExist() {
        when(productServiceClient.getById(999L)).thenReturn(Optional.empty());
        when(anthropicClient.sendMessage(any(), any(), any()))
                .thenReturn(toolUseResponse("add_to_cart", Map.of("productId", 999)))
                .thenReturn(finalTextResponse("I couldn't find that product."));

        var result = chatService.chat(null, 1L, "add product 999");

        assertThat(result.cartAdditions()).isEmpty();
    }
}
