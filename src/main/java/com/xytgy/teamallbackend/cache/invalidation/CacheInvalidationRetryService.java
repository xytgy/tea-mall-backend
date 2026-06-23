package com.xytgy.teamallbackend.cache.invalidation;

import com.xytgy.teamallbackend.cache.metrics.CacheMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 在独立有界线程池中执行有限次数的缓存失效重试。
 */
@Slf4j
@Service
public class CacheInvalidationRetryService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 100L;

    private final Executor executor;
    private final CacheMetrics metrics;

    public CacheInvalidationRetryService(
            @Qualifier("cacheInvalidationExecutor") Executor executor,
            CacheMetrics metrics) {
        this.executor = executor;
        this.metrics = metrics;
    }

    public void submit(String description, Runnable operation) {
        try {
            executor.execute(() -> retry(description, operation));
        } catch (RejectedExecutionException e) {
            metrics.recordInvalidationRetryExhausted();
            log.error("缓存失效重试队列已满, operation={}", description, e);
        }
    }

    private void retry(String description, Runnable operation) {
        long backoff = INITIAL_BACKOFF_MILLIS;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Thread.sleep(backoff);
                operation.run();
                metrics.recordInvalidationRetrySuccess();
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("缓存失效重试被中断, operation={}", description);
                return;
            } catch (RuntimeException e) {
                if (attempt == MAX_ATTEMPTS) {
                    metrics.recordInvalidationRetryExhausted();
                    log.error("缓存失效重试最终失败, operation={}, attempts={}",
                            description, MAX_ATTEMPTS, e);
                    return;
                }
                backoff *= 2;
            }
        }
    }
}
