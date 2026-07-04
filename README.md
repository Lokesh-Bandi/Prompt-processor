# Prompt Processor

Async REST service that accepts a batch of prompts, processes them in parallel against a mock LLM inference endpoint (with rate-limit retry), and lets callers poll for results at any time.

---

## Table of Contents

- [Requirements](#requirements)
- [Setup & Run](#setup--run)
- [Configuration](#configuration)
- [API Usage](#api-usage)
- [Concurrency Model](#concurrency-model)
- [Thread-Safety Design](#thread-safety-design)
- [Retry Strategy](#retry-strategy)
- [Log Output](#log-output)

---

## Requirements

| Tool | Version |
|------|---------|
| Java | 17+     |
| Maven | 3.8+   |

No database or external services are needed — everything runs in-memory.

---

## Setup & Run

```bash
# Clone and enter the project
git clone <repo-url>
cd prompt-processor

# Build
./mvnw clean package -DskipTests

# Run
./mvnw spring-boot:run
```

The server starts on **http://localhost:8080** with context path `/api`.

---

## Configuration

All tuneable values live in `src/main/resources/application.properties`.

```properties
# Thread pool
app.thread-pool.core-size=5       # threads always alive
app.thread-pool.max-size=15       # max threads under load
app.thread-pool.queue-capacity=200 # pending tasks before CallerRunsPolicy kicks in

# Mock inference
app.inference.mock-delay-min-ms=200          # min simulated latency
app.inference.mock-delay-max-ms=600          # max simulated latency
app.inference.rate-limit-probability=0.30    # random 429 chance per call
app.inference.rate-limit-every-nth=4         # force 429 every Nth call

# Retry (fixed delay)
app.retry.max-retries=3      # retries after first attempt
app.retry.backoff-ms=500     # fixed wait between retries
```

---

## API Usage

### Submit a job

```bash
# JSON array file
curl -X POST http://localhost:8080/api/v1/prompts/upload \
     -F "file=@prompts.json"

# Plain text file (one prompt per line, # lines ignored)
curl -X POST http://localhost:8080/api/v1/prompts/upload \
     -F "file=@prompts.txt"
```

**Response — 202 Accepted**

```json
{
  "jobId": "a3f1c2d4-...",
  "status": "ACCEPTED",
  "totalPrompts": 10,
  "message": "Job accepted. Poll the statusUrl to track progress.",
  "submittedAt": "2026-07-04T09:25:13Z",
  "statusUrl": "http://localhost:8080/api/v1/prompts/a3f1c2d4-.../result"
}
```

### Poll for results

```bash
curl http://localhost:8080/api/v1/prompts/{jobId}/result
```

**Response — 200 OK** (partial while processing, full when complete)

```json
{
  "jobId": "a3f1c2d4-...",
  "status": "COMPLETED",
  "totalPrompts": 10,
  "completedCount": 10,
  "successCount": 9,
  "failureCount": 1,
  "results": [
    {
      "prompt": "What is ML?",
      "response": "Mock LLM response...",
      "success": true,
      "attemptsUsed": 2,
      "processingTimeMs": 823
    }
  ]
}
```

**Job statuses**

| Status | Meaning |
|--------|---------|
| `ACCEPTED` | Job queued, no results yet |
| `PROCESSING` | At least one result has arrived |
| `COMPLETED` | All prompts succeeded |
| `PARTIAL_FAILURE` | All done, but one or more prompts failed all retries |

### Health check

```bash
curl http://localhost:8080/api/actuator/health
```

---

## Concurrency Model

```
HTTP Request Thread
       │
       ▼
┌─────────────────────┐
│  PromptController   │  POST /v1/prompts/upload
└────────┬────────────┘
         │ calls
         ▼
┌─────────────────────┐        ┌───────────────────────┐
│PromptProcessingService│──saves─▶  JobStoreService      │
│                     │        │  ConcurrentHashMap     │
│  creates JobEntry   │        └───────────────────────┘
│  returns 202 immediately
│                     │
│  for each prompt:   │
│  CompletableFuture  │
│  .runAsync(...)     │
└────────┬────────────┘
         │ submits N tasks
         ▼
┌─────────────────────────────────────────┐
│         promptWorkerExecutor            │
│   ThreadPoolExecutor (core=5, max=15)   │
│                                         │
│  ┌──────────┐ ┌──────────┐ ┌─────────┐ │
│  │ Worker 1 │ │ Worker 2 │ │Worker N │ │
│  └────┬─────┘ └────┬─────┘ └────┬────┘ │
└───────┼─────────────┼────────────┼──────┘
        │             │            │
        ▼             ▼            ▼
┌────────────────────────────────────────┐
│         MockInferenceService           │
│  (shared, thread-safe via AtomicLong)  │
└────────────────────────────────────────┘
        │             │            │
        ▼             ▼            ▼
┌────────────────────────────────────────┐
│              JobEntry                  │
│  results      → CopyOnWriteArrayList   │
│  successCount → AtomicInteger          │
│  failureCount → AtomicInteger          │
│  completedCount → AtomicInteger        │
│  status       → AtomicReference        │
└────────────────────────────────────────┘
        ▲
        │ GET /v1/prompts/{jobId}/result
        │ (any time, reads partial state safely)
┌───────┴──────────┐
│  Poll Request    │
│  (any thread)    │
└──────────────────┘
```

### Key design decisions

- **Submit returns immediately (202)** — the HTTP thread is never blocked by inference work.
- **One `CompletableFuture` per prompt** — prompts are independent and can all run in parallel up to the thread-pool limit.
- **`CallerRunsPolicy`** — if the queue fills up (> 200 tasks), the calling thread processes the task itself instead of dropping it.
- **`allOf()` completion hook** — a single hook on all futures logs the final job status server-side with no extra polling.

---

## Thread-Safety Design

| Shared State | Mechanism | Why |
|---|---|---|
| `JobEntry.results` | `CopyOnWriteArrayList` | Many workers append concurrently; reads (polling) are frequent and never block |
| `successCount`, `failureCount`, `completedCount` | `AtomicInteger` | Lock-free increments from N worker threads |
| `JobEntry.status` | `AtomicReference` | `compareAndSet` ensures exactly one thread transitions `ACCEPTED → PROCESSING` |
| `JobEntry.completedAt` | `volatile` | Written once by the last worker; visible to all polling threads without locking |
| `JobStoreService.store` | `ConcurrentHashMap` | Safe concurrent reads and writes across all request/worker threads |
| `MockInferenceService.requestCounter` | `AtomicLong` | Accurate global call count across all worker threads |

---

## Retry Strategy

On a `RateLimitException` (HTTP 429 from the mock service), each worker retries the **same prompt** with a fixed delay between attempts:

```
Attempt 1  ──▶  RateLimitException
                │
                └── sleep 500ms
Attempt 2  ──▶  RateLimitException
                │
                └── sleep 500ms
Attempt 3  ──▶  RateLimitException
                │
                └── sleep 500ms
Attempt 4  ──▶  RateLimitException  ──▶  record FAILURE (retries exhausted)

Attempt N  ──▶  SUCCESS             ──▶  record SUCCESS (return immediately)
```

Non-rate-limit exceptions (unexpected errors) are **not retried** — the worker records a failure immediately and moves on so it doesn't hold up the thread pool.

---

## Log Output

Logs are written to `logs/prompt-processor.log` (rolling, 10 MB per file, 7 days retained). Console output is suppressed.

```bash
tail -f logs/prompt-processor.log
```
