package com.example.promptprocessor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * Result produced by a single prompt worker after inference (or failure).
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PromptResult(
        String prompt,
        String response,
        boolean success,
        int attemptsUsed,
        String errorMessage,
        long processingTimeMs
) {
    public static PromptResult success(String prompt, String response, int attempts, long elapsedMs) {
        return PromptResult.builder()
                .prompt(prompt)
                .response(response)
                .success(true)
                .attemptsUsed(attempts)
                .processingTimeMs(elapsedMs)
                .build();
    }

    public static PromptResult failure(String prompt, String error, int attempts, long elapsedMs) {
        return PromptResult.builder()
                .prompt(prompt)
                .success(false)
                .errorMessage(error)
                .attemptsUsed(attempts)
                .processingTimeMs(elapsedMs)
                .build();
    }
}
