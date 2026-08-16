package com.cloudmart.genai.client;

import com.cloudmart.genai.dto.ChatMessage;
import com.cloudmart.genai.dto.MessagesRequest;
import com.cloudmart.genai.dto.MessagesResponse;
import com.cloudmart.genai.dto.ToolDefinition;
import com.cloudmart.genai.exception.AssistantUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.function.Supplier;

/**
 * Thin wrapper around the Anthropic Messages API (https://api.anthropic.com/v1/messages),
 * including tool-calling support. Calls are bounded by a read timeout (model
 * generation with tool use can take several seconds) and wrapped in a
 * retry + circuit breaker, same pattern as order-service's ProductClient.
 */
@Component
public class AnthropicClient {

    private static final String RESILIENCE_INSTANCE = "anthropic";
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 1024;

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public AnthropicClient(@Qualifier("anthropicRestTemplate") RestTemplate restTemplate,
                            @Value("${cloudmart.anthropic.api-key:}") String apiKey,
                            @Value("${cloudmart.anthropic.model:claude-sonnet-5}") String model,
                            CircuitBreakerRegistry circuitBreakerRegistry,
                            RetryRegistry retryRegistry) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.model = model;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(RESILIENCE_INSTANCE);
        this.retry = retryRegistry.retry(RESILIENCE_INSTANCE);
    }

    public MessagesResponse sendMessage(String systemPrompt, List<ChatMessage> messages, List<ToolDefinition> tools) {
        if (!StringUtils.hasText(apiKey)) {
            throw new AssistantUnavailableException(
                    "ANTHROPIC_API_KEY is not configured on genai-service");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", API_VERSION);
        headers.setContentType(MediaType.APPLICATION_JSON);

        var requestBody = new MessagesRequest(model, MAX_TOKENS, systemPrompt, messages, tools);
        var entity = new HttpEntity<>(requestBody, headers);

        Supplier<MessagesResponse> call = () ->
                restTemplate.postForObject(API_URL, entity, MessagesResponse.class);
        Supplier<MessagesResponse> decorated =
                CircuitBreaker.decorateSupplier(circuitBreaker, Retry.decorateSupplier(retry, call));

        try {
            return decorated.get();
        } catch (CallNotPermittedException ex) {
            throw new AssistantUnavailableException("AI assistant circuit breaker is open", ex);
        } catch (RestClientException ex) {
            throw new AssistantUnavailableException("AI assistant call failed: " + ex.getMessage(), ex);
        }
    }
}
