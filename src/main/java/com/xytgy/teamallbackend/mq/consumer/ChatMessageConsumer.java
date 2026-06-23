package com.xytgy.teamallbackend.mq.consumer;
import com.xytgy.teamallbackend.mq.constant.MqConstants;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.mq.handler.ChatDispatchHandler;
import com.xytgy.teamallbackend.mq.message.chat.ChatDispatchMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.name-server")
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_CHAT_MESSAGE,
        consumerGroup = MqConstants.GROUP_CHAT_MESSAGE,
        selectorExpression = MqConstants.TAG_MSG_DISPATCH
)
public class ChatMessageConsumer implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final ChatDispatchHandler chatDispatchHandler;

    @Override
    public void onMessage(String body) {
        try {
            ChatDispatchMessage message = objectMapper.readValue(body, ChatDispatchMessage.class);
            if (message.getReceiverId() == null
                    || message.getMessageId() == null
                    || message.getContent() == null) {
                throw new IllegalArgumentException("聊天分发消息字段不完整");
            }
            chatDispatchHandler.handle(
                    message.getReceiverId(),
                    message.getMessageId(),
                    message.getContent(),
                    message.getMsgType()
            );
        } catch (Exception e) {
            log.error("客服消息分发异常, body={}", body, e);
            throw new IllegalStateException("聊天分发消息消费失败", e);
        }
    }
}
