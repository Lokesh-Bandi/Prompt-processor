package com.example.promptprocessor.exception;

/**
 * Thrown by {@code MockInferenceService} to signal an HTTP 429 Too Many Requests.
 * Workers catch this and apply exponential-backoff retry.
 */
public class RateLimitException extends RuntimeException {

    private final int httpStatus;

    public RateLimitException(String message) {
        super(message);
        this.httpStatus = 429;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
