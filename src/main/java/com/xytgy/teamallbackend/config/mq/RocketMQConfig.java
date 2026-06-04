package com.xytgy.teamallbackend.config.mq;

import org.springframework.context.annotation.Configuration;

// P2#26: Producer 已改用 Spring Boot Starter 自动配置的 RocketMQTemplate，
// 不再手动创建 DefaultMQProducer，享受自动重连、健康检查等能力。
// Producer 相关配置见 application.yaml → rocketmq.producer.*
@Configuration
public class RocketMQConfig {
}
