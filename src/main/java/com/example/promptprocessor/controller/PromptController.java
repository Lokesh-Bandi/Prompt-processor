package com.example.promptprocessor.controller;

import com.example.promptprocessor.dto.AggregatedResponse;
import com.example.promptprocessor.dto.JobSubmissionResponse;
import com.example.promptprocessor.service.PromptProcessingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller exposing a multipart file upload endpoint and a result polling endpoint.
 *
 * <pre>
 * POST /api/v1/prompts/upload
 *      Content-Type: multipart/form-data
 *      Part "file": JSON array  ["p1","p2"] — OR — newline-separated plain text
 *      → 202 Accepted  { jobId, status, totalPrompts, statusUrl }
 *
 * GET  /api/v1/prompts/{jobId}/result
 *      → 200 OK  { jobId, status, results: [...] }   (partial while PROCESSING)
 * </pre>
 *
 * The upload endpoint returns immediately (HTTP 202); workers process prompts in the background.
 */
@Slf4j
@RestController
@RequestMapping("/v1/prompts")
@RequiredArgsConstructor
public class PromptController {

    private final PromptProcessingService processingService;
    private final ObjectMapper objectMapper;

    // -----------------------------------------------------------------------
    // File upload submission
    // -----------------------------------------------------------------------

    /**
     * Submit prompts by uploading a file.
     *
     * Supported file formats:
     *   • JSON array  — {@code ["prompt1","prompt2","prompt3"]}
     *   • Plain text  — one prompt per line (blank lines and # comments are ignored)
     *
     * Example:
     * <pre>
     * curl -X POST http://localhost:8080/api/v1/prompts/upload \
     *      -F "file=@prompts.json"
     *
     * curl -X POST http://localhost:8080/api/v1/prompts/upload \
     *      -F "file=@prompts.txt"
     * </pre>
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobSubmissionResponse> uploadPrompts(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<String> prompts = parsePromptFile(file);
        if (prompts.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("File upload '{}' — parsed {} prompt(s)", file.getOriginalFilename(), prompts.size());
        JobSubmissionResponse response = processingService.submitJob(prompts);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // -----------------------------------------------------------------------
    // Result / status polling
    // -----------------------------------------------------------------------

    /**
     * Poll for the current state of a submitted job.
     * Returns partial results while PROCESSING and the full aggregated result
     * once COMPLETED or PARTIAL_FAILURE.
     *
     * Example:
     * <pre>
     * curl http://localhost:8080/api/v1/prompts/{jobId}/result
     * </pre>
     */
    @GetMapping("/{jobId}/result")
    public ResponseEntity<AggregatedResponse> getResult(@PathVariable String jobId) {
        try {
            AggregatedResponse result = processingService.getJobResult(jobId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private List<String> parsePromptFile(MultipartFile file) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8).trim();

        // Detect JSON array
        if (content.startsWith("[")) {
            List<String> parsed = objectMapper.readValue(content, new TypeReference<>() {});
            return parsed.stream()
                    .filter(p -> p != null && !p.isBlank())
                    .collect(Collectors.toList());
        }

        // Fall back to newline-delimited plain text
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .collect(Collectors.toList());
        }
    }
}
