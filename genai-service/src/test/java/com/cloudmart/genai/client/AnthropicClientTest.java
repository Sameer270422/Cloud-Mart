package com.cloudmart.genai.client;

import com.cloudmart.genai.dto.ChatMessage;
import com.cloudmart.genai.dto.ContentBlocks;
import com.cloudmart.genai.dto.MessagesResponse;
import com.cloudmart.genai.exception.AssistantUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AnthropicClientTest {

    private RestTemplate restTemplate;

    private AnthropicClient client(String apiKey) {
        restTemplate = mock(RestTemplate.class);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(ResourceAccessException.class)
                .build();
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(10)
                .build();

        return new AnthropicClient(restTemplate, apiKey, "claude-sonnet-5",
                CircuitBreakerRegistry.of(cbConfig), RetryRegistry.of(retryConfig));
    }

    @Test
    void throwsWithoutCallingOutWhenApiKeyIsMissing() {
        AnthropicClient client = client("");

        assertThatThrownBy(() -> client.sendMessage("system", List.of(), List.of()))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");

        verifyNoInteractions(restTemplate);
    }

    @Test
    void returnsTheParsedResponseOnSuccess() {
        AnthropicClient client = client("test-key");
        var response = new MessagesResponse("id", "assistant",
                List.of(ContentBlocks.text("hello")), "end_turn");
        when(restTemplate.postForObject(anyString(), any(), eq(MessagesResponse.class)))
                .thenReturn(response);

        MessagesResponse result = client.sendMessage("system",
                List.of(ChatMessage.user(List.of(ContentBlocks.text("hi")))), List.of());

        assertThat(result.stopReason()).isEqualTo("end_turn");
        assertThat(ContentBlocks.extractText(result.content())).isEqualTo("hello");
    }

    @Test
    void wrapsTransportFailuresAfterRetriesExhausted() {
        AnthropicClient client = client("test-key");
        when(restTemplate.postForObject(anyString(), any(), eq(MessagesResponse.class)))
                .thenThrow(new ResourceAccessException("timed out"));

        assertThatThrownBy(() -> client.sendMessage("system", List.of(), List.of()))
                .isInstanceOf(AssistantUnavailableException.class);

        verify(restTemplate, times(2)).postForObject(anyString(), any(), eq(MessagesResponse.class));
    }
}
