package com.xytgy.teamallbackend.cache.invalidation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.cache.key.RedisGlobPattern;
import com.xytgy.teamallbackend.cache.local.LocalCacheInvalidator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 将本机缓存失效广播给其他应用实例。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheInvalidationSyncService implements MessageListener {

    static final String CHANNEL = "cache:invalidation:sync";

    private final LocalCacheInvalidator localInvalidator;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer listenerContainer;
    private final String instanceId = ManagementFactory.getRuntimeMXBean().getName()
            + ":" + UUID.randomUUID();

    @PostConstruct
    public void registerListener() {
        listenerContainer.start();
        listenerContainer.addMessageListener(this, new ChannelTopic(CHANNEL));
        log.info("缓存失效 Pub/Sub 监听已注册, channel={}", CHANNEL);
    }

    public void publishKey(String key) {
        publish(CacheInvalidationMessage.Operation.KEY, key);
    }

    public void publishPattern(String pattern) {
        publish(CacheInvalidationMessage.Operation.PATTERN, pattern);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            CacheInvalidationMessage payload = objectMapper.readValue(
                    message.getBody(), CacheInvalidationMessage.class);
            validate(payload);
            if (instanceId.equals(payload.sourceInstanceId())) {
                return;
            }
            switch (payload.operation()) {
                case KEY -> localInvalidator.invalidateKey(payload.target());
                case PATTERN -> localInvalidator.invalidatePattern(payload.target());
            }
        } catch (Exception e) {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            log.warn("忽略非法缓存失效消息, body={}, error={}",
                    abbreviate(body), e.getMessage());
            log.debug("缓存失效消息解析异常", e);
        }
    }

    private void publish(CacheInvalidationMessage.Operation operation, String target) {
        try {
            CacheInvalidationMessage payload = new CacheInvalidationMessage(
                    operation, target, instanceId, UUID.randomUUID().toString());
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("缓存失效消息发布失败, operation={}, target={}",
                    operation, target, e);
        }
    }

    private static void validate(CacheInvalidationMessage payload) {
        if (payload == null || payload.operation() == null
                || payload.target() == null || payload.target().isBlank()
                || payload.sourceInstanceId() == null || payload.eventId() == null) {
            throw new IllegalArgumentException("缓存失效消息字段不完整");
        }
        if (payload.operation() == CacheInvalidationMessage.Operation.PATTERN) {
            RedisGlobPattern.validate(payload.target());
        }
    }

    private static String abbreviate(String value) {
        return value.length() <= 256 ? value : value.substring(0, 256);
    }
}
