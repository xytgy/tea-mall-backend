package com.xytgy.teamallbackend.cache.hot;

import com.xytgy.teamallbackend.cache.metrics.CacheMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HotCacheLockServiceTest {

    @Test
    void acquiredLock_renewsBeforeClose_andStopsAfterClose() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);

        AtomicInteger renewCalls = new AtomicInteger();
        AtomicInteger unlockCalls = new AtomicInteger();
        CountDownLatch firstRenew = new CountDownLatch(1);

        doAnswer(invocation -> {
            renewCalls.incrementAndGet();
            firstRenew.countDown();
            return 1L;
        }).when(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), any(), any());

        doAnswer(invocation -> {
            unlockCalls.incrementAndGet();
            return 1L;
        }).when(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), any());

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            HotCacheLockService service = new HotCacheLockService(redisTemplate, new CacheMetrics(null), executor);
            var handle = service.tryAcquire("hot:key", Duration.ofMillis(120), Duration.ofMillis(20));

            assertTrue(handle.isPresent());
            assertTrue(firstRenew.await(300, TimeUnit.MILLISECONDS), "应至少发生一次续约");

            handle.get().close();
            int renewCountAfterClose = renewCalls.get();
            Thread.sleep(80L);

            assertEquals(1, unlockCalls.get(), "关闭时应只执行一次解锁");
            assertEquals(renewCountAfterClose, renewCalls.get(), "关闭后续约任务应停止");
        } finally {
            executor.shutdownNow();
        }
    }
}
