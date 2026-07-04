package com.example.promptprocessor.service;

import com.example.promptprocessor.model.JobEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobStoreServiceTest {

    private final JobStoreService store = new JobStoreService();

    @Test
    void savedJobCanBeFound() {
        JobEntry job = new JobEntry("abc-123", 5);
        store.save(job);
        assertThat(store.find("abc-123")).isPresent();
    }

    @Test
    void findReturnsEmptyForUnknownId() {
        assertThat(store.find("unknown")).isEmpty();
    }
}
