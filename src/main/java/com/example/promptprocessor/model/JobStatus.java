package com.example.promptprocessor.model;

public enum JobStatus {
    /** Job accepted and queued; workers not yet started. */
    ACCEPTED,

    /** At least one worker is actively processing prompts. */
    PROCESSING,

    /** All prompts completed — every one succeeded. */
    COMPLETED,

    /** All prompts finished but one or more failed after exhausting retries. */
    PARTIAL_FAILURE
}
