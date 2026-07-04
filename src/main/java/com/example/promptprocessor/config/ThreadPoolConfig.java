package com.example.promptprocessor.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ThreadPoolConfig {

    private final AppProperties appProperties;

    @Bean(name = "promptWorkerExecutor", destroyMethod = "shutdown")
    public ExecutorService promptWorkerExecutor() {
        AppProperties.ThreadPoolProperties cfg = appProperties.threadPool();

        ThreadFactory namedFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "prompt-worker-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                cfg.coreSize(),
                cfg.maxSize(),
                cfg.keepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(cfg.queueCapacity()),
                namedFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        log.info("Prompt worker thread pool initialized — core={}, max={}, queue={}",
                cfg.coreSize(), cfg.maxSize(), cfg.queueCapacity());

        return executor;
    }
}
