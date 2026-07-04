package com.example.promptprocessor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * Final aggregated response containing all prompt results once the job completes.
 * Also returned as partial results while the job is still PROCESSING.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AggregatedResponse(
        @JsonProperty("jobId")
        String jobId,

        @JsonProperty("status")
        String status,

        @JsonProperty("totalPrompts")
        int totalPrompts,

        @JsonProperty("completedCount")
        int completedCount,

        @JsonProperty("successCount")
        int successCount,

        @JsonProperty("failureCount")
        int failureCount,

        @JsonProperty("results")
        List<PromptResult> results,

        @JsonProperty("submittedAt")
        Instant submittedAt,

        @JsonProperty("completedAt")
        Instant completedAt,

        @JsonProperty("totalProcessingTimeMs")
        Long totalProcessingTimeMs
) {}
