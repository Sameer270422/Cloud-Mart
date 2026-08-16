package com.cloudmart.order.exception;

/**
 * Raised when product-service can't be reached at all - timeouts exhausted
 * retries, or the circuit breaker is open. Distinct from a client error
 * (e.g. 404/400) coming back from product-service, which is a normal
 * business response, not an infrastructure failure.
 */
public class ProductServiceUnavailableException extends RuntimeException {

    public ProductServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
