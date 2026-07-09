package com.xytgy.teamallbackend.module.flashsale.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
public class KafkaDemoController {

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 生产者：发送消息到 Kafka
     */
    @PostMapping("/send")
    public String sendMessage(@RequestParam String message) {
        kafkaTemplate.send("demo-topic", message);
        log.info("Kafka 消息发送成功: {}", message);
        return "发送成功: " + message;
    }

    /**
     * 消费者：监听 demo-topic，收到消息自动处理
     */
    @KafkaListener(topics = "demo-topic", groupId = "tea-mall-group")
    public void consumeMessage(String message) {
        log.info("Kafka 收到消息: {}", message);
        System.out.println("=== Kafka 消费者收到: " + message + " ===");
    }
}
