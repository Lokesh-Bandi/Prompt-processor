package com.example.promptprocessor.service;

import com.example.promptprocessor.model.JobEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for all active and completed jobs.
 *
 * In production this would be backed by Redis or a database to survive restarts
 * and support horizontal scaling; in this demo a ConcurrentHashMap is sufficient.
 */
@Slf4j
@Service
public class JobStoreService {

    private final ConcurrentHashMap<String, JobEntry> store = new ConcurrentHashMap<>();

    public void save(JobEntry job) {
        store.put(job.getJobId(), job);
        log.debug("Job {} stored (total jobs in memory: {})", job.getJobId(), store.size());
    }

    public Optional<JobEntry> find(String jobId) {
        return Optional.ofNullable(store.get(jobId));
    }

    public int activeJobCount() {
        return (int) store.values().stream()
                .filter(j -> !j.isFinished())
                .count();
    }
}
