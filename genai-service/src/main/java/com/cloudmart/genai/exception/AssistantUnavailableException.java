package com.cloudmart.genai.exception;

/**
 * Raised when the assistant can't complete a request - no API key
 * configured, Claude API unreachable, retries exhausted, or the circuit
 * breaker is open. Distinct from a normal validation error.
 */
public class AssistantUnavailableException extends RuntimeException {

    public AssistantUnavailableException(String message) {
        super(message);
    }

    public AssistantUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
