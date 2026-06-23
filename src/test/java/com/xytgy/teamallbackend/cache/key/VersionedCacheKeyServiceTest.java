package com.xytgy.teamallbackend.cache.key;

import com.xytgy.teamallbackend.cache.invalidation.CacheInvalidationRetryService;
import com.xytgy.teamallbackend.cache.metrics.CacheMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VersionedCacheKeyServiceTest {

    @Test
    void shouldBuildKeyWithCurrentVersion() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        when(values.get("product:version")).thenReturn("42");
        VersionedCacheKeyService service = new VersionedCacheKeyService(
                template,
                mock(CacheInvalidationRetryService.class),
                new CacheMetrics(null));

        String key = service.build("product:list:", "product:version", "1:20");

        assertEquals("product:list:v42:1:20", key);
    }

    @Test
    void shouldIncrementVersionAtomically() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        VersionedCacheKeyService service = new VersionedCacheKeyService(
                template,
                mock(CacheInvalidationRetryService.class),
                new CacheMetrics(null));

        service.incrementNow("product:version");

        verify(values).increment("product:version");
    }
}
