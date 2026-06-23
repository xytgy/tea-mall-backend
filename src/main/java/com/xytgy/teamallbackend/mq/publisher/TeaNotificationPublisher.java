package com.xytgy.teamallbackend.mq.publisher;

import com.xytgy.teamallbackend.mq.constant.MqConstants;
import com.xytgy.teamallbackend.mq.message.teacircle.TeaNotificationMessage;
import com.xytgy.teamallbackend.mq.producer.MqProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TeaNotificationPublisher {

    private final ObjectProvider<MqProducer> mqProducerProvider;

    public boolean publishCommentNotification(TeaNotificationMessage message) {
        return publish(MqConstants.TAG_COMMENT, message);
    }

    public boolean publishLikeNotification(TeaNotificationMessage message) {
        return publish(MqConstants.TAG_LIKE, message);
    }

    private boolean publish(String tag, TeaNotificationMessage message) {
        MqProducer mqProducer = mqProducerProvider.getIfAvailable();
        if (mqProducer == null) {
            log.warn("RocketMQ 不可用，茶友圈通知消息未发送: sourceId={}", message.getSourceId());
            return false;
        }
        mqProducer.send(
                MqConstants.TOPIC_TEA_NOTIFICATION,
                tag,
                String.valueOf(message.getSourceId()),
                message
        );
        return true;
    }
}
