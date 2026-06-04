package com.xytgy.teamallbackend.config.mq;

import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RocketMQConfig {

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    private DefaultMQProducer producer;

    @Bean
    public DefaultMQProducer defaultMQProducer() {
        producer = new DefaultMQProducer("tea-mall-producer-group");
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(3000);
        producer.setRetryTimesWhenSendFailed(2);
        try {
            producer.start();
        } catch (Exception e) {
            throw new RuntimeException("RocketMQ producer 启动失败", e);
        }
        return producer;
    }

    @PreDestroy
    public void destroy() {
        if (producer != null) {
            producer.shutdown();
        }
    }
}
