package com.xytgy.teamallbackend.config.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.mq.message.chat.ChatDispatchMessage;
import com.xytgy.teamallbackend.mq.publisher.ChatDispatchPublisher;
import com.xytgy.teamallbackend.module.chat.service.ChatService;
import com.xytgy.teamallbackend.module.chat.vo.ChatMessageVO;
import com.xytgy.teamallbackend.utils.JwtUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 聊天处理器。
 * <p>
 * 多实例部署时，通过 {@link WebSocketSessionRegistry} 实现跨实例消息推送：
 * <ul>
 *   <li>连接建立/关闭时在 Redis Hash 中注册/注销 userId -> instanceId 映射</li>
 *   <li>发送消息时先检查本地会话，不在本地则通过 Redis Pub/Sub 广播</li>
 *   <li>其他实例收到广播后从本地会话推送给目标用户</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    //常量
    private static final String TYPE_PING = "PING";
    private static final String TYPE_PONG = "PONG";
    private static final String TYPE_SEND_MSG = "SEND_MSG";
    private static final String TYPE_RECEIVE_MSG = "RECEIVE_MSG";
    private static final String TYPE_ACK_MSG = "ACK_MSG";

    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_ROLE = "role";
    private static final String ATTR_SHOP_ID = "shopId";
    private static final String FIELD_RECEIVER_ID = "receiverId";
    private static final String FIELD_MSG_TYPE = "msgType";
    private static final String FIELD_CONTENT = "content";

    private final ConcurrentHashMap<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, WebSocketSession> shopSessions = new ConcurrentHashMap<>();

    private static final String LOGIN_USER_KEY_PREFIX = "login:user:";

    private final JwtUtils jwtUtils;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final ChatDispatchPublisher chatDispatchPublisher;
    private final StringRedisTemplate stringRedisTemplate;
    private final WebSocketSessionRegistry sessionRegistry;

    /** Pub/Sub 消息类型常量 */
    private static final String PUBSUB_TYPE_CHAT_MSG = "CHAT_MESSAGE";
    private static final String PUBSUB_TYPE_NOTIFICATION = "NOTIFICATION";

    @PostConstruct
    public void init() {
        // 注册 Pub/Sub 消息回调，处理来自其他实例的消息
        sessionRegistry.setMessageCallback((type, payload) -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.convertValue(payload, Map.class);
                if (PUBSUB_TYPE_CHAT_MSG.equals(type)) {
                    Long receiverId = toLong(data.get("receiverId"));
                    Long messageId = toLong(data.get("messageId"));
                    String content = (String) data.get("content");
                    Integer msgType = toInt(data.get("msgType"));
                    if (receiverId != null) {
                        dispatchToReceiverLocal(receiverId, messageId, content, msgType);
                    }
                } else if (PUBSUB_TYPE_NOTIFICATION.equals(type)) {
                    Long userId = toLong(data.get("userId"));
                    String jsonPayload = (String) data.get("jsonPayload");
                    if (userId != null && jsonPayload != null) {
                        sendNotificationToLocal(userId, jsonPayload);
                    }
                }
            } catch (Exception e) {
                log.error("处理 Pub/Sub WebSocket 消息失败, type={}", type, e);
            }
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = null;
        try {
            if (session.getUri() != null) {
                token = UriComponentsBuilder.fromUri(session.getUri())
                        .build()
                        .getQueryParams()
                        .getFirst("token");
            }
        } catch (Exception ignored) {
            // ignore
        }

        if (token == null || token.isBlank()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        Map<String, Object> claims;
        try {
            claims = jwtUtils.parseToken(token);
        } catch (Exception e) {
            log.warn("WebSocket token 是违法的 sessionId={}", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        Long userId = parseLong(claims.get("id"));
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // 防止已登出用户通过旧 Token 建立 WebSocket 连接
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(LOGIN_USER_KEY_PREFIX + userId))) {
            log.warn("WebSocket 连接被拒绝：用户未在线, userId={}", userId);
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        Integer role = parseInt(claims.get("role"));
        Long shopId = parseLong(claims.get(ATTR_SHOP_ID));

        session.getAttributes().put(ATTR_USER_ID, userId);
        session.getAttributes().put(ATTR_ROLE, role);
        if (shopId != null) {
            session.getAttributes().put(ATTR_SHOP_ID, shopId);
        }

        registerSession(userId, shopId, session);
        log.info("WebSocket 已链接, userId={}, shopId={}, sessionId={}", userId, shopId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
        if (userId == null) {
            return;
        }

        JsonNode jsonNode;
        try {
            jsonNode = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            return;
        }
        String type = jsonNode.path("type").asText("");

        if (TYPE_PING.equals(type)) {
            Map<String, String> pong = new HashMap<>();
            pong.put("type", TYPE_PONG);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
            return;
        }

        if (TYPE_SEND_MSG.equals(type)) {
            JsonNode data = jsonNode.path("data");
            Long receiverId = data.hasNonNull(FIELD_RECEIVER_ID) ? data.get(FIELD_RECEIVER_ID).asLong() : null;
            String content = data.path(FIELD_CONTENT).asText(null);
            Integer msgType = data.hasNonNull(FIELD_MSG_TYPE) ? data.get(FIELD_MSG_TYPE).asInt() : 0;

            if (receiverId == null || content == null || content.isBlank()) {
                return;
            }

            ChatMessageVO savedMessage = chatService.saveMessage(userId, receiverId, content, msgType);

            // 通过 MQ 异步分发消息到接收方，解耦持久化与推送
            boolean published = chatDispatchPublisher.publish(
                    ChatDispatchMessage.builder()
                            .senderId(userId)
                            .receiverId(receiverId)
                            .messageId(savedMessage.getId())
                            .content(content)
                            .msgType(msgType)
                            .build()
            );
            if (!published) {
                log.warn("RocketMQ 不可用，消息未发送");
            }

            Map<String, Object> ack = new HashMap<>();
            ack.put("type", TYPE_ACK_MSG);
            ack.put("data", savedMessage);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
        }
    }

    /**
     * 供 MQ Consumer 调用，将消息推送到接收方的 WebSocket 会话。
     * 先检查本地会话，不在本地则通过 Redis Pub/Sub 广播到其他实例。
     */
    public void dispatchToReceiver(Long receiverId, Long messageId, String content, Integer msgType) {
        // 先尝试本地推送
        if (dispatchToReceiverLocal(receiverId, messageId, content, msgType)) {
            return;
        }

        // 不在本地，通过 Pub/Sub 广播（目标实例会从本地 session 推送）
        if (!sessionRegistry.isLocalUser(receiverId) && sessionRegistry.getUserInstance(receiverId) != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("receiverId", receiverId);
            payload.put("messageId", messageId);
            payload.put(FIELD_CONTENT, content);
            payload.put(FIELD_MSG_TYPE, msgType);
            sessionRegistry.broadcast(PUBSUB_TYPE_CHAT_MSG, payload);
        } else {
            log.debug("接收方不在线，跳过 WebSocket 推送, receiverId={}", receiverId);
        }
    }

    /**
     * 尝试通过本地会话推送消息给接收方。
     *
     * @return true 表示推送成功
     */
    private boolean dispatchToReceiverLocal(Long receiverId, Long messageId, String content, Integer msgType) {
        WebSocketSession receiverSession = userSessions.get(receiverId);
        if (receiverSession == null) {
            receiverSession = shopSessions.get(receiverId);
        }
        if (receiverSession == null || !receiverSession.isOpen()) {
            return false;
        }
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", TYPE_RECEIVE_MSG);
            Map<String, Object> data = new HashMap<>();
            data.put("id", messageId);
            data.put("senderId", null);
            data.put(FIELD_CONTENT, content);
            data.put(FIELD_MSG_TYPE, msgType);
            response.put("data", data);
            receiverSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            return true;
        } catch (Exception e) {
            log.error("WebSocket 推送消息失败, receiverId={}, messageId={}", receiverId, messageId, e);
            return false;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        unregisterSession(session);
        log.info("WebSocket disconnected, sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        unregisterSession(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private void registerSession(Long userId, Long shopId, WebSocketSession session) {
        WebSocketSession oldUserSession = userSessions.put(userId, session);
        if (oldUserSession != null && oldUserSession != session) {
            unregisterSession(oldUserSession);
            closeQuietly(oldUserSession, CloseStatus.NORMAL);
        }

        if (shopId != null) {
            WebSocketSession oldShopSession = shopSessions.put(shopId, session);
            if (oldShopSession != null && oldShopSession != session) {
                unregisterSession(oldShopSession);
                closeQuietly(oldShopSession, CloseStatus.NORMAL);
            }
        }

        // 注册到 Redis，记录 userId/shopId -> instanceId 映射
        sessionRegistry.registerUser(userId);
        if (shopId != null) {
            sessionRegistry.registerShop(shopId);
        }
    }

    private void unregisterSession(WebSocketSession session) {
        Object userIdObj = session.getAttributes().get(ATTR_USER_ID);
        if (userIdObj instanceof Long userId) {
            userSessions.remove(userId, session);
            sessionRegistry.unregisterUser(userId);
        }
        Object shopIdObj = session.getAttributes().get(ATTR_SHOP_ID);
        if (shopIdObj instanceof Long shopId) {
            shopSessions.remove(shopId, session);
            sessionRegistry.unregisterShop(shopId);
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (Exception ignored) {
            // ignore
        }
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 向指定用户推送通知消息（供其他模块复用，如秒杀失败通知）。
     * 先尝试本地推送，不在本地则通过 Redis Pub/Sub 广播。
     * 如果用户不在线则返回 false。
     */
    public boolean sendNotificationToUser(Long userId, String jsonPayload) {
        // 先尝试本地推送
        if (sendNotificationToLocal(userId, jsonPayload)) {
            return true;
        }

        // 不在本地，通过 Pub/Sub 广播
        if (!sessionRegistry.isLocalUser(userId) && sessionRegistry.getUserInstance(userId) != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("jsonPayload", jsonPayload);
            sessionRegistry.broadcast(PUBSUB_TYPE_NOTIFICATION, payload);
            return true;
        }
        return false;
    }

    /**
     * 尝试通过本地会话推送通知。
     *
     * @return true 表示推送成功
     */
    private boolean sendNotificationToLocal(Long userId, String jsonPayload) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(jsonPayload));
                return true;
            } catch (Exception e) {
                log.warn("WebSocket 推送通知失败, userId={}", userId, e);
            }
        }
        return false;
    }

    /**
     * 判断用户是否在线（先查本地，再查 Redis）。
     */
    public boolean isUserOnline(Long userId) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            return true;
        }
        // 本地没有，检查是否有其他实例持有该用户
        return sessionRegistry.getUserInstance(userId) != null;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return parseLong(value);
    }

    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return parseInt(value);
    }
}
