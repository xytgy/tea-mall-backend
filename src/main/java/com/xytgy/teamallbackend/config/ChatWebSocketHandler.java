package com.xytgy.teamallbackend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.module.chat.service.ChatService;
import com.xytgy.teamallbackend.module.chat.vo.ChatMessageVO;
import com.xytgy.teamallbackend.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final JwtUtils jwtUtils;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

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

        //得到用户id
        Long userId = parseLong(claims.get("id"));
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        //得到权限和商家ID
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

            Integer role = (Integer) session.getAttributes().get(ATTR_ROLE);
            Long shopId = (Long) session.getAttributes().get(ATTR_SHOP_ID);
            boolean isMerchant = (role != null && role == 1) || shopId != null;
            WebSocketSession receiverSession = isMerchant ? userSessions.get(receiverId) : shopSessions.get(receiverId);
            if (receiverSession != null && receiverSession.isOpen() && receiverSession != session) {
                Map<String, Object> response = new HashMap<>();
                response.put("type", TYPE_RECEIVE_MSG);
                response.put("data", savedMessage);
                receiverSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            }

            Map<String, Object> ack = new HashMap<>();
            ack.put("type", TYPE_ACK_MSG);
            ack.put("data", savedMessage);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
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
}
