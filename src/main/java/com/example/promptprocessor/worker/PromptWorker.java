package com.example.promptprocessor.worker;

import com.example.promptprocessor.dto.PromptResult;
import com.example.promptprocessor.exception.RateLimitException;
import com.example.promptprocessor.model.JobEntry;
import com.example.promptprocessor.service.MockInferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Worker that processes a single prompt against the mock inference endpoint.
 *
 * Retry strategy — fixed delay on HTTP 429:
 *   attempt 1 → immediate
 *   attempt 2..N → sleep initialBackoffMs between each retry (up to maxRetries)
 *
 * If all attempts are exhausted, the worker records a failure result so the
 * rest of the job can still complete.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptWorker {

    private final MockInferenceService inferenceService;

    @Value("${app.retry.max-retries}")
    private int maxRetries;

    @Value("${app.retry.backoff-ms}")
    private long backoffMs;

    /**
     * Process one prompt, reporting the result (success or failure) back to the
     * {@link JobEntry}.  Designed to be submitted as a {@link Runnable} to the
     * worker thread pool.
     *
     * @param prompt   the text to infer on
     * @param job      the parent job that aggregates all results
     */
    public void process(String prompt, JobEntry job) {
        long startTime = System.currentTimeMillis();
        int attempt = 0;
        String lastError = null;

        while (attempt <= maxRetries) {
            attempt++;
            log.debug("[Worker:{}] job={} attempt={}/{} prompt='{}'",
                    Thread.currentThread().getName(), job.getJobId(), attempt, maxRetries + 1, truncate(prompt));

            try {
                String response = inferenceService.infer(prompt);
                long elapsed    = System.currentTimeMillis() - startTime;

                PromptResult result = PromptResult.success(prompt, response, attempt, elapsed);
                job.recordResult(result);

                log.info("[Worker:{}] job={} SUCCESS attempt={} elapsed={}ms prompt='{}'",
                        Thread.currentThread().getName(), job.getJobId(), attempt, elapsed, truncate(prompt));
                return;

            } catch (RateLimitException e) {
                lastError = e.getMessage();
                log.warn("[Worker:{}] job={} RATE LIMITED (attempt {}/{}) prompt='{}' — {}",
                        Thread.currentThread().getName(), job.getJobId(),
                        attempt, maxRetries + 1, truncate(prompt), e.getMessage());

                if (attempt > maxRetries) {
                    break;
                }

                sleep(backoffMs, job.getJobId(), attempt, prompt);

            } catch (Exception e) {
                // Non-retryable error — fail immediately
                lastError = e.getMessage();
                log.error("[Worker:{}] job={} FATAL error on prompt='{}': {}",
                        Thread.currentThread().getName(), job.getJobId(), truncate(prompt), e.getMessage(), e);
                break;
            }
        }

        // All retries exhausted or non-retryable failure
        long elapsed = System.currentTimeMillis() - startTime;
        PromptResult result = PromptResult.failure(
                prompt,
                "Failed after " + attempt + " attempt(s): " + lastError,
                attempt,
                elapsed
        );
        job.recordResult(result);
        log.error("[Worker:{}] job={} FAILED after {} attempts elapsed={}ms prompt='{}'",
                Thread.currentThread().getName(), job.getJobId(), attempt, elapsed, truncate(prompt));
    }

    private void sleep(long ms, String jobId, int attempt, String prompt) {
        try {
            log.debug("[Worker:{}] job={} backing off {}ms before retry attempt={} prompt='{}'",
                    Thread.currentThread().getName(), jobId, ms, attempt + 1, truncate(prompt));
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[Worker:{}] Back-off sleep interrupted", Thread.currentThread().getName());
        }
    }

    private String truncate(String text) {
        return (text != null && text.length() > 60) ? text.substring(0, 60) + "..." : text;
    }
}
