package com.xytgy.teamallbackend.mq.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.mq.entity.MqRetryRecord;
import com.xytgy.teamallbackend.mq.mapper.MqRetryRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RocketMQTemplate.class)
public class MqProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final MqRetryRecordMapper retryRecordMapper;

    /**
     * 指数退避第一次间隔（秒），后续按 10, 30, 90, 270 递增
     */
    private static final int FIRST_BACKOFF_SECONDS = 10;
    private static final int MAX_RETRY_COUNT = 4;

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
            Message<String> msg = MessageBuilder.withPayload(body)
                    .setHeader("KEYS", key)
                    .build();
            rocketMQTemplate.syncSend(topic + ":" + tag, msg);
            log.debug("MQ 消息发送成功: topic={}, tag={}, key={}", topic, tag, key);
        } catch (Exception e) {
            log.error("MQ 消息发送失败，写入本地重试表: topic={}, tag={}, key={}", topic, tag, key, e);
            saveRetryRecord(topic, tag, key, body, null, e);
        }
    }

    private void doSendDelay(String topic, String tag, String key, String body, int delayLevel) {
        try {
            Message<String> msg = MessageBuilder.withPayload(body)
                    .setHeader("KEYS", key)
                    .build();
            rocketMQTemplate.syncSend(topic + ":" + tag, msg, 3000, delayLevel);
            log.debug("MQ 延迟消息发送成功: topic={}, tag={}, key={}, delayLevel={}", topic, tag, key, delayLevel);
        } catch (Exception e) {
            log.error("MQ 延迟消息发送失败，写入本地重试表: topic={}, tag={}, key={}", topic, tag, key, e);
            saveRetryRecord(topic, tag, key, body, delayLevel, e);
        }
    }

    /**
     * 发送失败时写入本地重试表，等待定时补偿任务重试
     */
    private void saveRetryRecord(String topic, String tag, String key, String body, Integer delayLevel, Exception error) {
        try {
            MqRetryRecord record = MqRetryRecord.builder()
                    .topic(topic)
                    .tags(tag)
                    .messageKey(key)
                    .messageBody(body)
                    .retryCount(0)
                    .maxRetryCount(MAX_RETRY_COUNT)
                    .status("PENDING")
                    .nextRetryTime(LocalDateTime.now().plusSeconds(FIRST_BACKOFF_SECONDS))
                    .delayLevel(delayLevel)
                    .lastError(truncateError(error))
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            retryRecordMapper.insert(record);
            log.info("MQ 重试记录已保存: id={}, topic={}, tag={}, key={}", record.getId(), topic, tag, key);
        } catch (Exception ex) {
            log.error("MQ 重试记录保存失败: topic={}, tag={}, key={}", topic, tag, key, ex);
        }
    }

    private String truncateError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("MQ 消息序列化失败", e);
        }
    }
}
