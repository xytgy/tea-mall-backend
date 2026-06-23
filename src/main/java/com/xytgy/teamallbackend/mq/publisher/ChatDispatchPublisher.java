package com.xytgy.teamallbackend.mq.publisher;

import com.xytgy.teamallbackend.mq.constant.MqConstants;
import com.xytgy.teamallbackend.mq.message.chat.ChatDispatchMessage;
import com.xytgy.teamallbackend.mq.producer.MqProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatDispatchPublisher {

    private final ObjectProvider<MqProducer> mqProducerProvider;

    public boolean publish(ChatDispatchMessage message) {
        MqProducer mqProducer = mqProducerProvider.getIfAvailable();
        if (mqProducer == null) {
            log.warn("RocketMQ 不可用，聊天分发消息未发送: messageId={}", message.getMessageId());
            return false;
        }
        mqProducer.send(
                MqConstants.TOPIC_CHAT_MESSAGE,
                MqConstants.TAG_MSG_DISPATCH,
                String.valueOf(message.getMessageId()),
                message
        );
        return true;
    }
}
