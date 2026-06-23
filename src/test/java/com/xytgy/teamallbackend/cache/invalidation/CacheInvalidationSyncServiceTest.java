package com.xytgy.teamallbackend.cache.invalidation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.cache.local.LocalCacheInvalidator;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheInvalidationSyncServiceTest {

    @Test
    void shouldApplyRemoteKeyInvalidationLocally() throws Exception {
        LocalCacheInvalidator localInvalidator = mock(LocalCacheInvalidator.class);
        ObjectMapper objectMapper = new ObjectMapper();
        CacheInvalidationSyncService service = new CacheInvalidationSyncService(
                localInvalidator,
                mock(StringRedisTemplate.class),
                objectMapper,
                mock(RedisMessageListenerContainer.class));
        CacheInvalidationMessage payload = new CacheInvalidationMessage(
                CacheInvalidationMessage.Operation.KEY,
                "product:1",
                "remote-instance",
                "event-1");
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(
                objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8));

        service.onMessage(message, null);

        verify(localInvalidator).invalidateKey("product:1");
    }

    @Test
    void shouldIgnoreMalformedMessage() {
        LocalCacheInvalidator localInvalidator = mock(LocalCacheInvalidator.class);
        CacheInvalidationSyncService service = new CacheInvalidationSyncService(
                localInvalidator,
                mock(StringRedisTemplate.class),
                new ObjectMapper(),
                mock(RedisMessageListenerContainer.class));
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn("not-json".getBytes(StandardCharsets.UTF_8));

        service.onMessage(message, null);

        verify(localInvalidator, org.mockito.Mockito.never()).invalidateKey(
                org.mockito.ArgumentMatchers.anyString());
    }
}
