package com.xytgy.teamallbackend.mq.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis Pub/Sub 监听容器配置。
 * Spring Boot 不会自动创建此 Bean，需手动注册。
 */
@Configuration
class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}

/**
 * 秒杀本地库存缓存管理器。
 * <p>
 * 每个 App 实例维护 JVM 内存库存快照，仅在库存为 0 时快速拒绝。
 * Redis 是唯一真相源，本地不做扣减决策。
 * <p>
 * 通过 Redis Pub/Sub 接收库存变更广播，保持多实例最终一致性。
 */
@Slf4j
@Component
public class FlashSaleCacheManager {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;

    private static final String STOCK_CHANNEL = "flash:stock:sync";
    private static final ConcurrentHashMap<Long, AtomicLong> LOCAL_STOCK = new ConcurrentHashMap<>();

    public FlashSaleCacheManager(StringRedisTemplate stringRedisTemplate,
                                  RedisMessageListenerContainer redisMessageListenerContainer) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisMessageListenerContainer = redisMessageListenerContainer;
    }

    @PostConstruct
    public void init() {
        MessageListenerAdapter adapter = new MessageListenerAdapter(this, "onStockMessage");
        redisMessageListenerContainer.addMessageListener(adapter, new ChannelTopic(STOCK_CHANNEL));
        log.info("秒杀本地缓存 Pub/Sub 监听已启动, channel={}", STOCK_CHANNEL);
    }

    /**
     * 判断本地库存是否为 0（快速拒绝）
     */
    public boolean isLocalStockEmpty(Long productId) {
        AtomicLong stock = LOCAL_STOCK.get(productId);
        return stock == null || stock.get() <= 0;
    }

    /**
     * 同步更新本地库存（Redis 返回最新值后调用）
     */
    // P2#17: 使用 computeIfAbsent + set() 避免整体替换 AtomicLong 对象
    // 原方案在高并发下，线程 A 拿到旧对象后线程 B 替换为新对象，A 的后续读取仍是旧值
    public void syncLocalStock(Long productId, int redisStock) {
        LOCAL_STOCK.computeIfAbsent(productId, k -> new AtomicLong()).set(redisStock);
    }

    /**
     * 预热本地库存
     */
    public void warmUp(Long productId, int stock) {
        LOCAL_STOCK.put(productId, new AtomicLong(stock));
    }

    /**
     * 清除本地库存
     */
    public void clear(Long productId) {
        LOCAL_STOCK.remove(productId);
    }

    /**
     * 发布库存变更事件到 Redis Pub/Sub
     */
    public void broadcastStockChange(Long productId, int newStock) {
        String message = productId + ":" + newStock;
        stringRedisTemplate.convertAndSend(STOCK_CHANNEL, message);
    }

    /**
     * 接收库存变更广播，更新本地缓存
     */
    public void onStockMessage(Message message, byte[] pattern) {
        try {
            String msg = new String(message.getBody());
            String[] parts = msg.split(":");
            Long productId = Long.parseLong(parts[0]);
            int newStock = Integer.parseInt(parts[1]);
            LOCAL_STOCK.put(productId, new AtomicLong(newStock));
            log.debug("收到库存广播: productId={}, stock={}", productId, newStock);
        } catch (Exception e) {
            log.error("解析库存广播消息失败", e);
        }
    }

    /**
     * 预热活动基础信息到 Redis Hash（消除 buy() 中的 DB 查询）。
     * 每次预热或补货时调用，数据 24 小时后自动过期。
     */
    public void cacheActivity(Long flashSaleId, Map<String, String> activityInfo) {
        String key = "flash:activity:" + flashSaleId;
        stringRedisTemplate.opsForHash().putAll(key, activityInfo);
        stringRedisTemplate.expire(key, 24, TimeUnit.HOURS);
    }

    /**
     * 获取缓存的活动信息，缓存未命中时返回空 Map
     */
    public Map<Object, Object> getCachedActivity(Long flashSaleId) {
        return stringRedisTemplate.opsForHash().entries("flash:activity:" + flashSaleId);
    }

    /**
     * 预热活动商品信息到 Redis Hash
     */
    public void cacheActivityProduct(Long flashSaleId, Long productId, Map<String, String> productInfo) {
        String key = "flash:activity_product:" + flashSaleId + ":" + productId;
        stringRedisTemplate.opsForHash().putAll(key, productInfo);
        stringRedisTemplate.expire(key, 24, TimeUnit.HOURS);
    }

    /**
     * 获取缓存的活动商品信息
     */
    public Map<Object, Object> getCachedActivityProduct(Long flashSaleId, Long productId) {
        return stringRedisTemplate.opsForHash().entries("flash:activity_product:" + flashSaleId + ":" + productId);
    }
}
