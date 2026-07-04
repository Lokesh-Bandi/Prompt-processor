package com.example.promptprocessor.model;

import com.example.promptprocessor.dto.PromptResult;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Internal representation of a submitted job.
 * Thread-safe — workers write results concurrently via CopyOnWriteArrayList
 * and AtomicInteger counters.
 */
@Slf4j
@Getter
public class JobEntry {

    private final String jobId;
    private final int totalPrompts;
    private final Instant submittedAt;

    private final AtomicReference<JobStatus> status = new AtomicReference<>(JobStatus.ACCEPTED);
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    /** Ordered list of results as they arrive; may be partial while PROCESSING. */
    private final List<PromptResult> results = new CopyOnWriteArrayList<>();

    private volatile Instant completedAt;

    public JobEntry(String jobId, int totalPrompts) {
        this.jobId = jobId;
        this.totalPrompts = totalPrompts;
        this.submittedAt = Instant.now();
    }

    /**
     * Called by each worker thread when its prompt finishes (success or failure).
     * Automatically transitions the job to COMPLETED / PARTIAL_FAILURE when every
     * prompt has been resolved.
     */
    public void recordResult(PromptResult result) {
        results.add(result);

        if (result.success()) {
            successCount.incrementAndGet();
        } else {
            failureCount.incrementAndGet();
        }

        int done = completedCount.incrementAndGet();

        // Transition to PROCESSING as soon as the first result arrives
        status.compareAndSet(JobStatus.ACCEPTED, JobStatus.PROCESSING);

        if (done >= totalPrompts) {
            completedAt = Instant.now();
            JobStatus finalStatus = failureCount.get() > 0
                    ? JobStatus.PARTIAL_FAILURE
                    : JobStatus.COMPLETED;
            status.set(finalStatus);
            log.info("Job {} finished — status={}, success={}, failure={}",
                    jobId, finalStatus, successCount.get(), failureCount.get());
        }
    }

    public boolean isFinished() {
        JobStatus s = status.get();
        return s == JobStatus.COMPLETED || s == JobStatus.PARTIAL_FAILURE;
    }
}
