package com.example.promptprocessor.service;

import com.example.promptprocessor.dto.AggregatedResponse;
import com.example.promptprocessor.dto.JobSubmissionResponse;
import com.example.promptprocessor.model.JobEntry;
import com.example.promptprocessor.worker.PromptWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Orchestrates the full prompt-processing pipeline:
 *
 * 1.  Receives a list of prompts from the controller.
 * 2.  Creates a {@link JobEntry} and persists it in the store.
 * 3.  Returns a {@link JobSubmissionResponse} to the caller immediately (non-blocking).
 * 4.  Submits one {@link CompletableFuture} per prompt to the worker thread pool.
 *     Each future calls {@link PromptWorker#process} which handles retry logic.
 * 5.  Attaches an allOf() completion hook that logs the final job status.
 *
 * Callers poll {@link #getJobResult(String)} at any time to see current progress
 * or the final aggregated result.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptProcessingService {

    private final PromptWorker promptWorker;
    private final JobStoreService jobStoreService;

    @Qualifier("promptWorkerExecutor")
    private final ExecutorService workerExecutor;

    /**
     * Accepts a list of prompts, starts background processing, and immediately
     * returns a lightweight submission receipt with the job ID.
     */
    public JobSubmissionResponse submitJob(List<String> prompts) {
        String jobId = UUID.randomUUID().toString();
        JobEntry job = new JobEntry(jobId, prompts.size());
        jobStoreService.save(job);

        log.info("Job {} accepted — {} prompt(s) submitted to thread pool", jobId, prompts.size());

        // Fan out: one CompletableFuture per prompt, all sharing the worker thread pool
        List<CompletableFuture<Void>> futures = prompts.stream()
                .map(prompt -> CompletableFuture.runAsync(
                        () -> promptWorker.process(prompt, job),
                        workerExecutor
                ))
                .toList();

        // Aggregate completion hook — purely for server-side logging
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((ignored, ex) -> {
                    if (ex != null) {
                        log.error("Job {} encountered unexpected error during aggregation: {}",
                                jobId, ex.getMessage(), ex);
                    } else {
                        log.info("Job {} — all {} futures resolved. Final status: {}",
                                jobId, prompts.size(), job.getStatus().get());
                    }
                });

        String statusUrl = buildStatusUrl(jobId);

        return JobSubmissionResponse.builder()
                .jobId(jobId)
                .status(job.getStatus().get().name())
                .totalPrompts(prompts.size())
                .message("Job accepted. Poll the statusUrl to track progress and retrieve results.")
                .submittedAt(job.getSubmittedAt())
                .statusUrl(statusUrl)
                .build();
    }

    /**
     * Returns the current (possibly partial) aggregated result for a job.
     * Safe to call at any time — results are added to the job concurrently by workers.
     */
    public AggregatedResponse getJobResult(String jobId) {
        JobEntry job = jobStoreService.find(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        Long totalProcessingMs = null;
        if (job.isFinished() && job.getCompletedAt() != null) {
            totalProcessingMs = job.getCompletedAt().toEpochMilli() - job.getSubmittedAt().toEpochMilli();
        }

        return AggregatedResponse.builder()
                .jobId(job.getJobId())
                .status(job.getStatus().get().name())
                .totalPrompts(job.getTotalPrompts())
                .completedCount(job.getCompletedCount().get())
                .successCount(job.getSuccessCount().get())
                .failureCount(job.getFailureCount().get())
                .results(List.copyOf(job.getResults()))
                .submittedAt(job.getSubmittedAt())
                .completedAt(job.getCompletedAt())
                .totalProcessingTimeMs(totalProcessingMs)
                .build();
    }

    private String buildStatusUrl(String jobId) {
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/v1/prompts/{jobId}/result")
                    .buildAndExpand(jobId)
                    .toUriString();
        } catch (Exception e) {
            return "/api/v1/prompts/" + jobId + "/result";
        }
    }
}
