package com.example.promptprocessor.service;

import com.example.promptprocessor.exception.RateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates a rate-limited LLM inference endpoint.
 *
 * Rate-limiting: every Nth call receives HTTP 429.
 * On success, sleeps for a random duration within the configured delay window.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockInferenceService {

    @Value("${app.inference.rate-limit-every-nth}")
    private int rateLimitEveryNth;

    @Value("${app.inference.mock-delay-min-ms}")
    private long mockDelayMinMs;

    @Value("${app.inference.mock-delay-max-ms}")
    private long mockDelayMaxMs;

    /** Global request counter across all threads — used for periodic rate-limiting. */
    private final AtomicLong requestCounter = new AtomicLong(0);

    /**
     * Calls the mock inference endpoint.
     *
     * @param prompt the input text
     * @return simulated model response
     * @throws RateLimitException if the mock endpoint returns HTTP 429
     */
    public String infer(String prompt) throws RateLimitException {
        long callNumber = requestCounter.incrementAndGet();

        if (callNumber % rateLimitEveryNth == 0) {
            log.warn("[MockInference] HTTP 429 — call #{} (every {}th request)", callNumber, rateLimitEveryNth);
            throw new RateLimitException("HTTP 429 Too Many Requests (call #" + callNumber + ")");
        }

        // Simulate inference latency
        long delay = mockDelayMinMs
                + ThreadLocalRandom.current().nextLong(mockDelayMaxMs - mockDelayMinMs + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Inference interrupted", e);
        }

        String response = buildMockResponse(prompt, callNumber);
        log.debug("[MockInference] call #{} succeeded for prompt '{}' in {}ms", callNumber, truncate(prompt), delay);
        return response;
    }

    private String buildMockResponse(String prompt, long callNumber) {
        return String.format(
                "Mock LLM response [call #%d]: The answer to \"%s\" involves careful reasoning about " +
                "language patterns, contextual understanding, and semantic relationships. " +
                "This response was generated in approximately %dms simulated inference time.",
                callNumber,
                truncate(prompt),
                mockDelayMinMs
        );
    }

    private String truncate(String text) {
        return text.length() > 60 ? text.substring(0, 60) + "..." : text;
    }
}
