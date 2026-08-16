package com.cloudmart.genai.service;

import com.cloudmart.genai.client.AnthropicClient;
import com.cloudmart.genai.client.ProductServiceClient;
import com.cloudmart.genai.dto.ChatMessage;
import com.cloudmart.genai.dto.ContentBlocks;
import com.cloudmart.genai.dto.ProductMatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Ranks the live product catalog against a natural-language query using
 * Claude's own language understanding, rather than a vector index. At this
 * catalog size the whole thing fits comfortably in context, so there's no
 * need to stand up embeddings + a vector store; that would be the natural
 * next step if the catalog grew into the thousands.
 *
 * Used directly by the standalone search endpoint, and as the search_products
 * tool the chat assistant calls - one implementation, two entry points.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticSearchService {

    private static final Pattern JSON_ARRAY = Pattern.compile("\\[[^\\[\\]]*]", Pattern.DOTALL);

    private static final String RANKING_SYSTEM_PROMPT = """
            You are a product search ranking engine for an e-commerce catalog.
            You will be given the full catalog as JSON and a shopper's natural
            language query. Consider synonyms, use-case, and intent, not just
            literal keyword overlap - e.g. "something to keep coffee hot"
            should match an insulated bottle even with no shared words.

            Respond with ONLY a JSON array of matching product ids as numbers,
            most relevant first, capped at the requested limit. No prose, no
            markdown code fences, no explanation - just the array. If nothing
            in the catalog is relevant, respond with [].
            """;

    private final AnthropicClient anthropicClient;
    private final ProductServiceClient productServiceClient;
    private final ObjectMapper objectMapper;

    public List<ProductMatch> search(String query, int limit) {
        List<ProductServiceClient.ProductDto> catalog = productServiceClient.listAll();
        if (catalog.isEmpty()) {
            return List.of();
        }

        String userPrompt;
        try {
            userPrompt = "Catalog:\n" + objectMapper.writeValueAsString(catalog)
                    + "\n\nQuery: \"" + query + "\"\nLimit: " + limit;
        } catch (Exception ex) {
            log.error("Failed to serialize catalog for semantic search", ex);
            return List.of();
        }

        var response = anthropicClient.sendMessage(
                RANKING_SYSTEM_PROMPT,
                List.of(ChatMessage.user(List.of(ContentBlocks.text(userPrompt)))),
                List.of());

        List<Long> rankedIds = parseIds(ContentBlocks.extractText(response.content()));

        Map<Long, ProductServiceClient.ProductDto> byId = catalog.stream()
                .collect(Collectors.toMap(ProductServiceClient.ProductDto::getId, p -> p, (a, b) -> a));

        return rankedIds.stream()
                .limit(limit)
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(ProductMatch::from)
                .toList();
    }

    // Claude is instructed to return a bare JSON array, but models
    // occasionally wrap output in prose or code fences anyway - extract the
    // first bracketed array rather than trusting the response is clean.
    private List<Long> parseIds(String rawResponse) {
        Matcher matcher = JSON_ARRAY.matcher(rawResponse);
        if (!matcher.find()) {
            log.warn("Semantic search response had no parseable JSON array: {}", rawResponse);
            return List.of();
        }
        try {
            List<Number> ids = objectMapper.readValue(matcher.group(), new com.fasterxml.jackson.core.type.TypeReference<List<Number>>() {});
            return ids.stream().map(Number::longValue).toList();
        } catch (Exception ex) {
            log.warn("Failed to parse semantic search response as a JSON array: {}", rawResponse, ex);
            return List.of();
        }
    }
}
