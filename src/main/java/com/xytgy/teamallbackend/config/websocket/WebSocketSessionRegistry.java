package com.xytgy.teamallbackend.config.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * WebSocket 会话注册表，用于多实例部署时的跨实例消息推送。
 * <p>
 * 核心机制：
 * <ul>
 *   <li>每个实例启动时生成唯一 instanceId（UUID）</li>
 *   <li>使用 Redis Hash 存储 userId -> instanceId 和 shopId -> instanceId 映射</li>
 *   <li>使用 Redis Pub/Sub 广播消息到所有实例</li>
 *   <li>接收方实例通过 instanceId 判断是否在本地，若在本地则直接推送</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketSessionRegistry {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** 当前实例唯一标识 */
    private final String instanceId = UUID.randomUUID().toString();

    /** Redis Hash key: userId -> instanceId */
    private static final String USER_HASH_KEY = "ws:user:instance";
    /** Redis Hash key: shopId -> instanceId */
    private static final String SHOP_HASH_KEY = "ws:shop:instance";
    /** Redis Pub/Sub 频道 */
    private static final String CHANNEL = "ws:broadcast:message";

    /** Pub/Sub 容器（在 init 中启动） */
    private RedisMessageListenerContainer pubSubContainer;

    /**
     * 启动 Redis Pub/Sub 监听。
     * 使用独立的 RedisMessageListenerContainer 以避免与 Spring 默认容器冲突。
     */
    @PostConstruct
    public void init() {
        pubSubContainer = new RedisMessageListenerContainer();
        pubSubContainer.setConnectionFactory(stringRedisTemplate.getConnectionFactory());
        pubSubContainer.afterPropertiesSet();

        MessageListener listener = (message, pattern) -> {
            try {
                String body = new String(message.getBody());
                Map<String, Object> msg = objectMapper.readValue(body, Map.class);

                // 忽略自己发出的消息
                String senderInstanceId = (String) msg.get("senderInstanceId");
                if (instanceId.equals(senderInstanceId)) {
                    return;
                }

                String type = (String) msg.get("type");
                Object payload = msg.get("payload");

                // 交给监听器回调处理（由 ChatWebSocketHandler 注册）
                if (messageCallback != null) {
                    messageCallback.onMessage(type, payload);
                }
            } catch (Exception e) {
                log.error("处理 Redis Pub/Sub 消息失败", e);
            }
        };

        pubSubContainer.addMessageListener(listener, new ChannelTopic(CHANNEL));
        pubSubContainer.start();
        log.info("WebSocketSessionRegistry 已启动, instanceId={}", instanceId);
    }

    @PreDestroy
    public void destroy() {
        if (pubSubContainer != null) {
            pubSubContainer.stop();
        }
        log.info("WebSocketSessionRegistry 已停止, instanceId={}", instanceId);
    }

    // ======================== 回调接口 ========================

    /** 消息回调，由 ChatWebSocketHandler 注册 */
    private MessageCallback messageCallback;

    public interface MessageCallback {
        void onMessage(String type, Object payload);
    }

    public void setMessageCallback(MessageCallback callback) {
        this.messageCallback = callback;
    }

    // ======================== 注册 / 注销 ========================

    /**
     * 注册用户到当前实例。
     */
    public void registerUser(Long userId) {
        stringRedisTemplate.opsForHash().put(USER_HASH_KEY, String.valueOf(userId), instanceId);
    }

    /**
     * 注册店铺到当前实例。
     */
    public void registerShop(Long shopId) {
        stringRedisTemplate.opsForHash().put(SHOP_HASH_KEY, String.valueOf(shopId), instanceId);
    }

    /**
     * 注销用户（仅当当前实例为注册实例时才删除）。
     */
    public void unregisterUser(Long userId) {
        String registered = (String) stringRedisTemplate.opsForHash().get(USER_HASH_KEY, String.valueOf(userId));
        if (instanceId.equals(registered)) {
            stringRedisTemplate.opsForHash().delete(USER_HASH_KEY, String.valueOf(userId));
        }
    }

    /**
     * 注销店铺（仅当当前实例为注册实例时才删除）。
     */
    public void unregisterShop(Long shopId) {
        String registered = (String) stringRedisTemplate.opsForHash().get(SHOP_HASH_KEY, String.valueOf(shopId));
        if (instanceId.equals(registered)) {
            stringRedisTemplate.opsForHash().delete(SHOP_HASH_KEY, String.valueOf(shopId));
        }
    }

    // ======================== 实例查询 ========================

    /**
     * 查询用户所在的实例 ID。
     */
    public String getUserInstance(Long userId) {
        return (String) stringRedisTemplate.opsForHash().get(USER_HASH_KEY, String.valueOf(userId));
    }

    /**
     * 查询店铺所在的实例 ID。
     */
    public String getShopInstance(Long shopId) {
        return (String) stringRedisTemplate.opsForHash().get(SHOP_HASH_KEY, String.valueOf(shopId));
    }

    /**
     * 判断用户是否在当前实例。
     */
    public boolean isLocalUser(Long userId) {
        return instanceId.equals(getUserInstance(userId));
    }

    /**
     * 判断店铺是否在当前实例。
     */
    public boolean isLocalShop(Long shopId) {
        return instanceId.equals(getShopInstance(shopId));
    }

    // ======================== Pub/Sub 广播 ========================

    /**
     * 广播消息到所有实例。
     *
     * @param type    消息类型
     * @param payload 消息体
     */
    public void broadcast(String type, Object payload) {
        try {
            Map<String, Object> msg = Map.of(
                    "senderInstanceId", instanceId,
                    "type", type,
                    "payload", payload
            );
            stringRedisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.error("WebSocket Pub/Sub 广播失败, type={}", type, e);
        }
    }

    public String getInstanceId() {
        return instanceId;
    }
}
