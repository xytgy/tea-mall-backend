package com.xytgy.teamallbackend.cache.bloom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.cache.bloom.event.ProductCreatedEvent;
import com.xytgy.teamallbackend.properties.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BloomFilterSyncServiceTest {

    private BloomFilterManager manager;
    private StringRedisTemplate redisTemplate;
    private BloomFilterSyncService service;

    @BeforeEach
    void setUp() {
        CacheProperties properties = new CacheProperties();
        properties.getBloom().setProductExpectedInsertions(10_000);
        properties.getBloom().setUserExpectedInsertions(10_000);
        manager = new BloomFilterManager(properties);
        manager.replaceProductFilter(manager.newProductFilter());
        manager.replaceUserFilter(manager.newUserFilter());
        redisTemplate = mock(StringRedisTemplate.class);
        service = new BloomFilterSyncService(
                manager,
                redisTemplate,
                new ObjectMapper(),
                mock(RedisMessageListenerContainer.class)
        );
    }

    @Test
    void shouldApplyLocallyAndPublishAfterProductEvent() {
        service.onProductCreated(new ProductCreatedEvent(1001L));

        assertTrue(manager.mightContainProduct(1001L));
        verify(redisTemplate).convertAndSend(eq(BloomFilterSyncService.CHANNEL), anyString());
    }

    @Test
    void shouldConsumeValidRedisMessage() throws Exception {
        BloomFilterSyncMessage payload = new BloomFilterSyncMessage(
                BloomFilterSyncMessage.EntityType.USER, 2001L);
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(
                new ObjectMapper().writeValueAsString(payload).getBytes(StandardCharsets.UTF_8));

        service.onMessage(message, null);

        assertTrue(manager.mightContainUser(2001L));
    }

    @Test
    void shouldIgnoreMalformedRedisMessage() {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn("not-json".getBytes(StandardCharsets.UTF_8));

        service.onMessage(message, null);

        assertFalse(manager.mightContainUser(2001L));
    }
}
