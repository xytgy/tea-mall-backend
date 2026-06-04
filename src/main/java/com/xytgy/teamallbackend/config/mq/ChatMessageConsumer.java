package com.xytgy.teamallbackend.config.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.config.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_CHAT_MESSAGE,
        consumerGroup = "tea-mall-chat-dispatch-group",
        selectorExpression = MqConstants.TAG_MSG_DISPATCH
)
public class ChatMessageConsumer implements RocketMQListener<String> {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            Long receiverId = json.get("receiverId").asLong();
            Long messageId = json.get("messageId").asLong();
            String content = json.get("content").asText();
            Integer msgType = json.get("msgType").asInt(0);

            chatWebSocketHandler.dispatchToReceiver(receiverId, messageId, content, msgType);
        } catch (Exception e) {
            log.error("客服消息分发异常, body={}", body, e);
        }
    }
}
