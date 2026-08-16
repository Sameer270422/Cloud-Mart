package com.cloudmart.genai.service;

import com.cloudmart.genai.client.AnthropicClient;
import com.cloudmart.genai.client.ProductServiceClient;
import com.cloudmart.genai.dto.ContentBlocks;
import com.cloudmart.genai.dto.MessagesResponse;
import com.cloudmart.genai.dto.ProductMatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticSearchServiceTest {

    @Mock
    private AnthropicClient anthropicClient;

    @Mock
    private ProductServiceClient productServiceClient;

    private SemanticSearchService service;

    @BeforeEach
    void setUp() {
        service = new SemanticSearchService(anthropicClient, productServiceClient, new ObjectMapper());
    }

    private ProductServiceClient.ProductDto product(long id, String name) {
        var p = new ProductServiceClient.ProductDto();
        p.setId(id);
        p.setName(name);
        p.setDescription("desc");
        p.setCategory("cat");
        p.setPrice(new BigDecimal("9.99"));
        p.setStockQuantity(5);
        return p;
    }

    @Test
    void returnsEmptyWithoutCallingClaudeWhenCatalogIsEmpty() {
        when(productServiceClient.listAll()).thenReturn(List.of());

        List<ProductMatch> result = service.search("keyboard", 5);

        assertThat(result).isEmpty();
    }

    @Test
    void ranksAndMapsProductsFromAPlainJsonIdArray() {
        when(productServiceClient.listAll()).thenReturn(
                List.of(product(1, "Keyboard"), product(2, "Monitor"), product(3, "Chair")));
        when(anthropicClient.sendMessage(any(), any(), any()))
                .thenReturn(new MessagesResponse("id", "assistant",
                        List.of(ContentBlocks.text("[3, 1]")), "end_turn"));

        List<ProductMatch> result = service.search("comfortable seating", 5);

        assertThat(result).extracting(ProductMatch::id).containsExactly(3L, 1L);
    }

    @Test
    void toleratesProseOrCodeFencesAroundTheJsonArray() {
        when(productServiceClient.listAll()).thenReturn(List.of(product(1, "Keyboard")));
        when(anthropicClient.sendMessage(any(), any(), any()))
                .thenReturn(new MessagesResponse("id", "assistant",
                        List.of(ContentBlocks.text("Here you go:\n```json\n[1]\n```")), "end_turn"));

        List<ProductMatch> result = service.search("keyboard", 5);

        assertThat(result).extracting(ProductMatch::id).containsExactly(1L);
    }

    @Test
    void returnsEmptyWhenNoMatchesAreRelevant() {
        when(productServiceClient.listAll()).thenReturn(List.of(product(1, "Keyboard")));
        when(anthropicClient.sendMessage(any(), any(), any()))
                .thenReturn(new MessagesResponse("id", "assistant",
                        List.of(ContentBlocks.text("[]")), "end_turn"));

        List<ProductMatch> result = service.search("spacecraft", 5);

        assertThat(result).isEmpty();
    }

    @Test
    void respectsTheRequestedLimit() {
        when(productServiceClient.listAll()).thenReturn(
                List.of(product(1, "A"), product(2, "B"), product(3, "C")));
        when(anthropicClient.sendMessage(any(), any(), any()))
                .thenReturn(new MessagesResponse("id", "assistant",
                        List.of(ContentBlocks.text("[1, 2, 3]")), "end_turn"));

        List<ProductMatch> result = service.search("anything", 2);

        assertThat(result).hasSize(2);
    }
}
