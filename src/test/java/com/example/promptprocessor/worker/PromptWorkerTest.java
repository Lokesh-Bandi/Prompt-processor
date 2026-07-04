package com.example.promptprocessor.worker;

import com.example.promptprocessor.exception.RateLimitException;
import com.example.promptprocessor.model.JobEntry;
import com.example.promptprocessor.service.MockInferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromptWorkerTest {

    @Mock
    private MockInferenceService inferenceService;

    private PromptWorker worker;

    @BeforeEach
    void setUp() {
        worker = new PromptWorker(inferenceService);
        ReflectionTestUtils.setField(worker, "maxRetries", 2);
        ReflectionTestUtils.setField(worker, "backoffMs", 10L);
    }

    @Test
    void recordsSuccessOnFirstAttempt() throws RateLimitException {
        when(inferenceService.infer(anyString())).thenReturn("ok");

        JobEntry job = new JobEntry("job-1", 1);
        worker.process("prompt", job);

        assertThat(job.getSuccessCount().get()).isEqualTo(1);
        assertThat(job.getFailureCount().get()).isEqualTo(0);
    }

    @Test
    void recordsFailureWhenAllRetriesExhausted() throws RateLimitException {
        when(inferenceService.infer(anyString())).thenThrow(new RateLimitException("429"));

        JobEntry job = new JobEntry("job-2", 1);
        worker.process("prompt", job);

        assertThat(job.getFailureCount().get()).isEqualTo(1);
        assertThat(job.getSuccessCount().get()).isEqualTo(0);
        // 1 initial + 2 retries = 3 total calls
        verify(inferenceService, times(3)).infer(anyString());
    }
}
