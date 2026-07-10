package com.xytgy.teamallbackend.cache.bloom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.cache.bloom.event.ProductCreatedEvent;
import com.xytgy.teamallbackend.cache.bloom.event.UserCreatedEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;

/**
 * 事务提交后同步新增 ID，并通过 Redis Pub/Sub 广播到其他应用实例。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BloomFilterSyncService implements MessageListener {

    static final String CHANNEL = "cache:bloom:sync";

    private final BloomFilterManager bloomFilterManager;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer listenerContainer;

    @PostConstruct
    public void registerListener() {
        listenerContainer.start();
        listenerContainer.addMessageListener(this, new ChannelTopic(CHANNEL));
        log.info("BloomFilter Pub/Sub 监听已注册, channel={}", CHANNEL);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductCreated(ProductCreatedEvent event) {
        syncAndPublish(new BloomFilterSyncMessage(
                BloomFilterSyncMessage.EntityType.PRODUCT, event.productId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(UserCreatedEvent event) {
        syncAndPublish(new BloomFilterSyncMessage(
                BloomFilterSyncMessage.EntityType.USER, event.userId()));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            BloomFilterSyncMessage syncMessage = objectMapper.readValue(
                    message.getBody(), BloomFilterSyncMessage.class);
            applyLocally(syncMessage);
        } catch (Exception e) {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            log.warn("忽略非法 BloomFilter 同步消息, body={}, error={}",
                    abbreviate(body), e.getMessage());
            log.debug("BloomFilter 同步消息解析异常", e);
        }
    }

    private void syncAndPublish(BloomFilterSyncMessage message) {
        try {
            applyLocally(message);
            stringRedisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            // 数据已提交，BloomFilter 只是防穿透优化；同步失败不能反向影响主业务。
            log.error("BloomFilter 新增 ID 同步失败, type={}, id={}",
                    message.type(), message.id(), e);
        }
    }

    private void applyLocally(BloomFilterSyncMessage message) {
        if (message == null || message.type() == null
                || message.id() == null || message.id() <= 0) {
            throw new IllegalArgumentException("BloomFilter 同步消息字段不合法");
        }
        switch (message.type()) {
            case PRODUCT -> bloomFilterManager.addProductId(message.id());
            case USER -> bloomFilterManager.addUserId(message.id());
        }
    }

    private static String abbreviate(String value) {
        int maxLength = 256;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
