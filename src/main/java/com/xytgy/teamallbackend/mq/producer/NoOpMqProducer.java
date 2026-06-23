package com.xytgy.teamallbackend.mq.producer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 当 RocketMQ 不可用时的空实现，避免开发环境启动失败
 */
@Slf4j
@Component
@ConditionalOnMissingBean(MqProducer.class)
public class NoOpMqProducer {

    public void send(String topic, String tag, String key, Object payload) {
        log.warn("RocketMQ 不可用，消息丢弃: topic={}, tag={}, key={}", topic, tag, key);
    }

    public void sendDelay(String topic, String tag, String key, Object payload, int delayLevel) {
        log.warn("RocketMQ 不可用，延迟消息丢弃: topic={}, tag={}, key={}", topic, tag, key);
    }
}