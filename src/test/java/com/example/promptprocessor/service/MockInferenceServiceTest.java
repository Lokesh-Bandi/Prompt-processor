package com.example.promptprocessor.service;

import com.example.promptprocessor.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class MockInferenceServiceTest {

    private MockInferenceService service;

    @BeforeEach
    void setUp() {
        service = new MockInferenceService();
        ReflectionTestUtils.setField(service, "rateLimitEveryNth", 4);
        ReflectionTestUtils.setField(service, "mockDelayMinMs", 10L);
        ReflectionTestUtils.setField(service, "mockDelayMaxMs", 20L);
    }

    @Test
    void successfulCallReturnsResponse() throws RateLimitException {
        String response = service.infer("What is Java?");
        assertThat(response).isNotBlank();
    }

    @Test
    void everyNthCallThrowsRateLimitException() {
        assertThatCode(() -> service.infer("p1")).doesNotThrowAnyException();
        assertThatCode(() -> service.infer("p2")).doesNotThrowAnyException();
        assertThatCode(() -> service.infer("p3")).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.infer("p4"))
                .isInstanceOf(RateLimitException.class);
    }
}
