package com.xytgy.teamallbackend.cache.invalidation;

import com.xytgy.teamallbackend.cache.local.LocalCacheInvalidator;
import com.xytgy.teamallbackend.cache.metrics.CacheMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheKeyCleanerTest {

    private StringRedisTemplate redisTemplate;
    private LocalCacheInvalidator localInvalidator;
    private CacheInvalidationSyncService syncService;
    private CacheInvalidationRetryService retryService;
    private CacheKeyCleaner cleaner;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        localInvalidator = mock(LocalCacheInvalidator.class);
        syncService = mock(CacheInvalidationSyncService.class);
        retryService = mock(CacheInvalidationRetryService.class);
        cleaner = new CacheKeyCleaner(
                redisTemplate, localInvalidator, syncService,
                retryService, new CacheMetrics(null));
    }

    @Test
    void shouldDeleteRedisBeforeLocalCache() {
        cleaner.delete("product:1");

        var ordered = inOrder(redisTemplate, localInvalidator, syncService);
        ordered.verify(redisTemplate).delete("product:1");
        ordered.verify(localInvalidator).invalidateKey("product:1");
        ordered.verify(syncService).publishKey("product:1");
    }

    @Test
    void shouldInvalidateLocalAndScheduleRetryWhenRedisFails() {
        doThrow(new RuntimeException("redis unavailable"))
                .when(redisTemplate).delete("product:1");

        cleaner.delete("product:1");

        verify(localInvalidator).invalidateKey("product:1");
        verify(retryService).submit(any(), any());
        verify(syncService).publishKey("product:1");
    }

    @Test
    void shouldDeletePatternInBatchesAndCloseCursor() {
        @SuppressWarnings("unchecked")
        Cursor<String> cursor = mock(Cursor.class);
        var iterator = List.of("product:1", "product:2").iterator();
        when(cursor.hasNext()).thenAnswer(invocation -> iterator.hasNext());
        when(cursor.next()).thenAnswer(invocation -> iterator.next());
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(redisTemplate.delete(anyCollection())).thenReturn(2L);

        cleaner.deleteByPattern("product:*");

        verify(redisTemplate).delete(anyCollection());
        verify(cursor).close();
        verify(localInvalidator).invalidatePattern("product:*");
        verify(syncService).publishPattern("product:*");
    }

    @Test
    void shouldRejectDangerousInputBeforeTouchingRedis() {
        assertThrows(IllegalArgumentException.class, () -> cleaner.delete(" "));
        assertThrows(IllegalArgumentException.class, () -> cleaner.deleteByPattern("*"));

        verify(redisTemplate, never()).delete(any(String.class));
        verify(redisTemplate, never()).scan(any());
    }
}
