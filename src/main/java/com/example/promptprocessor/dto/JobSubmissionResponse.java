package com.example.promptprocessor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.Instant;

/**
 * Immediate response returned to the caller upon job submission.
 * The caller receives this before any prompts are processed.
 */
@Builder
public record JobSubmissionResponse(
        @JsonProperty("jobId")
        String jobId,

        @JsonProperty("status")
        String status,

        @JsonProperty("totalPrompts")
        int totalPrompts,

        @JsonProperty("message")
        String message,

        @JsonProperty("submittedAt")
        Instant submittedAt,

        @JsonProperty("statusUrl")
        String statusUrl
) {}
