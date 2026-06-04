package com.xytgy.teamallbackend.config.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqProducer {

    private final DefaultMQProducer producer;
    private final ObjectMapper objectMapper;

    /**
     * 发送即时消息；若在事务内则延迟到提交后发送，避免事务回滚导致消息泄漏
     */
    public void send(String topic, String tag, String key, Object payload) {
        String body = toJson(payload);
        Runnable task = () -> doSend(topic, tag, key, body);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            task.run();
                        }
                    });
        } else {
            task.run();
        }
    }

    /**
     * 发送延迟消息，仅在事务外使用（订单创建后的超时取消）
     */
    public void sendDelay(String topic, String tag, String key, Object payload, int delayLevel) {
        String body = toJson(payload);
        Runnable task = () -> doSendDelay(topic, tag, key, body, delayLevel);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            task.run();
                        }
                    });
        } else {
            task.run();
        }
    }

    private void doSend(String topic, String tag, String key, String body) {
        try {
            Message msg = new Message(topic, tag, key, body.getBytes(StandardCharsets.UTF_8));
            SendResult result = producer.send(msg);
            log.debug("MQ 消息发送成功: topic={}, tag={}, key={}, msgId={}", topic, tag, key, result.getMsgId());
        } catch (Exception e) {
            log.error("MQ 消息发送失败: topic={}, tag={}, key={}", topic, tag, key, e);
        }
    }

    private void doSendDelay(String topic, String tag, String key, String body, int delayLevel) {
        try {
            Message msg = new Message(topic, tag, key, body.getBytes(StandardCharsets.UTF_8));
            msg.setDelayTimeLevel(delayLevel);
            SendResult result = producer.send(msg);
            log.debug("MQ 延迟消息发送成功: topic={}, tag={}, key={}, delayLevel={}, msgId={}", topic, tag, key, delayLevel, result.getMsgId());
        } catch (Exception e) {
            log.error("MQ 延迟消息发送失败: topic={}, tag={}, key={}", topic, tag, key, e);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("MQ 消息序列化失败", e);
        }
    }
}
