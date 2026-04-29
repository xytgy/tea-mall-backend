package com.xytgy.teamallbackend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.module.chat.service.ChatService;
import com.xytgy.teamallbackend.module.chat.vo.ChatMessageVO;
import com.xytgy.teamallbackend.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final ConcurrentHashMap<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final JwtUtils jwtUtils;
    private final ChatService chatService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri().getQuery();
        if (query != null && query.contains("token=")) {
            String token = query.substring(query.indexOf("token=") + 6);
            if (token.contains("&")) {
                token = token.substring(0, token.indexOf("&"));
            }
            try {
                Map<String, Object> claims = jwtUtils.parseToken(token);
                // 根据 UserServiceImpl 中的逻辑，如果是普通用户存的 key 是 "id"，如果是商家也包含 "id"（并且额外有 "shopId"）
                Long userId = Long.valueOf(claims.get("id").toString());
                
                // 如果是商家，获取 shopId。这里前端通常会根据 role == 1 来判定是否是商家。
                // 商家连接时，他的 receiverId 可能就是这个 userId 对应的 shopId 或者就是 userId。
                // 但根据你的前端截图，左侧“暂无消息”，有可能是 merchantId 传错了。
                // 为了万无一失，我们在 session 里同时存入 userId 和 shopId（如果有）。
                Object shopIdObj = claims.get("shopId");
                Long shopId = shopIdObj != null ? Long.valueOf(shopIdObj.toString()) : null;
                
                sessions.put(userId, session);
                session.getAttributes().put("userId", userId);
                if (shopId != null) {
                    session.getAttributes().put("shopId", shopId);
                    // 很多时候前端发消息时，填的 merchantId 是店铺 ID 而不是用户 ID！
                    // 所以我们也把 shopId 对应的映射存一份，确保前端用 shopId 发消息时能找到这个 session
                    sessions.put(shopId, session); 
                }
                
                System.out.println("WebSocket 鉴权通过，用户 ID: " + userId + (shopId != null ? ", 商家/店铺 ID: " + shopId : ""));
                return;
            } catch (Exception e) {
                System.err.println("WebSocket Token 解析失败或无效: " + e.getMessage());
                e.printStackTrace();
            }
        }
        session.close(CloseStatus.NOT_ACCEPTABLE);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) return;

        String payload = message.getPayload();
        JsonNode jsonNode = objectMapper.readTree(payload);
        String type = jsonNode.has("type") ? jsonNode.get("type").asText() : "";

        if ("PING".equals(type)) {
            Map<String, String> pong = new HashMap<>();
            pong.put("type", "PONG");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
            return;
        }

        if ("SEND_MSG".equals(type)) {
            JsonNode data = jsonNode.get("data");
            Long receiverId = data.get("receiverId").asLong();
            String content = data.get("content").asText();
            Integer msgType = data.has("msgType") ? data.get("msgType").asInt() : 0;

            // 补充：检查 receiverId 和 content
            if (receiverId == null || content == null || content.trim().isEmpty()) {
                System.err.println("发送消息参数不合法，丢弃消息");
                return;
            }

            // 保存消息
            ChatMessageVO savedMessage = chatService.saveMessage(userId, receiverId, content, msgType);
            
            // 为了修复显示为对方发的消息的问题，我们在 WebSocket 转发给对方时，
            // 不需要修改 savedMessage，因为 savedMessage 里记录的 senderId 是真实的发送者 ID。
            // 只要 receiverSession 取到的是正确的接收者即可。
            
            // 发送给接收者 (如果接收者是商家，receiverId 可能是商家的 shopId，也可能是商家的 userId)
            // 所以我们通过 sessions Map 中保存的映射去找接收者。
            WebSocketSession receiverSession = sessions.get(receiverId);
            // 补充检查：如果接收者不在线，再试着查一下如果是商铺，可能关联的用户在线；如果是用户，可能关联的商铺在线
            // 这里因为刚才我们在鉴权时已经把 userId 和 shopId 都放进了 sessions，所以一般直接 get 就能拿到。

            // 【关键修复】商家端显示为对方发送的问题
            // 商家端在发送消息时，传递的 userId 是商家的真实用户 ID。
            // 但买家在跟商家聊天时，认的是 shopId！所以当商家发消息时，前端可能期望看到的是 shopId 作为 sender。
            // 如果后端在存库时记录的是 userId，前端就会认为这是一个第三方或者买家自己，从而把气泡渲染在左侧！
            
            // 我们在保存完消息后，由于在 ChatServiceImpl 中我们已经处理过，
            // 这里拿到的 savedMessage 已经是处理过的，其 senderId 如果是商家已经替换为 shopId 了。

            // 修复商家发消息的重复及显示问题：
            // 确保不把消息 push 回发送它的 WebSocketSession 实例。
            if (receiverSession != null && receiverSession.isOpen() && !receiverSession.getId().equals(session.getId())) {
                Map<String, Object> response = new HashMap<>();
                response.put("type", "RECEIVE_MSG");
                response.put("data", savedMessage);
                receiverSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            }
            
            // 注意：因为前端已经做了“乐观更新”把消息直接上屏了，
            // 这里不再把同一条消息推送给发送方本人，避免前端页面出现重复的两条气泡！
            // 补充：返回包含真实 ID 的 ACK 消息，让前端用真实的 ID 替换本地的临时消息，避免 Vue :key 错乱
            Map<String, Object> ack = new HashMap<>();
            ack.put("type", "ACK_MSG");
            ack.put("data", savedMessage);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            sessions.remove(userId);
        }
        Long shopId = (Long) session.getAttributes().get("shopId");
        if (shopId != null) {
            sessions.remove(shopId);
        }
    }
}
