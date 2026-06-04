package com.xytgy.teamallbackend.config.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.config.mq.MqConstants;
import com.xytgy.teamallbackend.config.mq.MqProducer;
import com.xytgy.teamallbackend.module.chat.service.ChatService;
import com.xytgy.teamallbackend.module.chat.vo.ChatMessageVO;
import com.xytgy.teamallbackend.utils.JwtUtils;
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

    private final ConcurrentHashMap<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, WebSocketSession> shopSessions = new ConcurrentHashMap<>();

    private static final String LOGIN_USER_KEY_PREFIX = "login:user:";

    private final JwtUtils jwtUtils;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final MqProducer mqProducer;
    private final StringRedisTemplate stringRedisTemplate;

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
        Long shopId = parseLong(claims.get("shopId"));

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
            Long receiverId = data.hasNonNull("receiverId") ? data.get("receiverId").asLong() : null;
            String content = data.path("content").asText(null);
            Integer msgType = data.hasNonNull("msgType") ? data.get("msgType").asInt() : 0;

            if (receiverId == null || content == null || content.isBlank()) {
                return;
            }

            ChatMessageVO savedMessage = chatService.saveMessage(userId, receiverId, content, msgType);

            // 通过 MQ 异步分发消息到接收方，解耦持久化与推送
            Map<String, Object> dispatchMsg = new HashMap<>();
            dispatchMsg.put("senderId", userId);
            dispatchMsg.put("receiverId", receiverId);
            dispatchMsg.put("messageId", savedMessage.getId());
            dispatchMsg.put("content", content);
            dispatchMsg.put("msgType", msgType);
            mqProducer.send(MqConstants.TOPIC_CHAT_MESSAGE, MqConstants.TAG_MSG_DISPATCH,
                    String.valueOf(savedMessage.getId()), dispatchMsg);

            Map<String, Object> ack = new HashMap<>();
            ack.put("type", TYPE_ACK_MSG);
            ack.put("data", savedMessage);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
        }
    }

    /**
     * 供 MQ Consumer 调用，将消息推送到接收方的 WebSocket 会话
     */
    public void dispatchToReceiver(Long receiverId, Long messageId, String content, Integer msgType) {
        WebSocketSession receiverSession = userSessions.get(receiverId);
        if (receiverSession == null) {
            receiverSession = shopSessions.get(receiverId);
        }
        if (receiverSession == null || !receiverSession.isOpen()) {
            log.debug("接收方不在线，跳过 WebSocket 推送, receiverId={}", receiverId);
            return;
        }
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", TYPE_RECEIVE_MSG);
            Map<String, Object> data = new HashMap<>();
            data.put("id", messageId);
            data.put("senderId", null);
            data.put("content", content);
            data.put("msgType", msgType);
            response.put("data", data);
            receiverSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (Exception e) {
            log.error("WebSocket 推送消息失败, receiverId={}, messageId={}", receiverId, messageId, e);
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
    }

    private void unregisterSession(WebSocketSession session) {
        Object userIdObj = session.getAttributes().get(ATTR_USER_ID);
        if (userIdObj instanceof Long userId) {
            userSessions.remove(userId, session);
        }
        Object shopIdObj = session.getAttributes().get(ATTR_SHOP_ID);
        if (shopIdObj instanceof Long shopId) {
            shopSessions.remove(shopId, session);
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (Exception ignored) {
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
     * 如果用户不在线则返回 false。
     */
    public boolean sendNotificationToUser(Long userId, String jsonPayload) {
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
     * 判断用户是否在线
     */
    public boolean isUserOnline(Long userId) {
        WebSocketSession session = userSessions.get(userId);
        return session != null && session.isOpen();
    }
}
