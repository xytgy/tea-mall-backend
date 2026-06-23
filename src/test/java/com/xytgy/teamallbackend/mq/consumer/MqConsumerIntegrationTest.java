package com.xytgy.teamallbackend.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.mq.handler.ChatDispatchHandler;
import com.xytgy.teamallbackend.mq.handler.FlashOrderHandler;
import com.xytgy.teamallbackend.mq.handler.OrderTimeoutHandler;
import com.xytgy.teamallbackend.mq.handler.PaymentNotifyHandler;
import com.xytgy.teamallbackend.mq.handler.TeaNotificationHandler;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig(MqConsumerIntegrationTest.TestConfig.class)
@TestPropertySource(properties = "rocketmq.name-server=test:9876")
class MqConsumerIntegrationTest {

    @jakarta.annotation.Resource
    private FlashOrderMqListener flashOrderMqListener;
    @jakarta.annotation.Resource
    private OrderTimeoutConsumer orderTimeoutConsumer;
    @jakarta.annotation.Resource
    private PaymentNotifyConsumer paymentNotifyConsumer;
    @jakarta.annotation.Resource
    private ChatMessageConsumer chatMessageConsumer;
    @jakarta.annotation.Resource
    private TeaNotificationConsumer teaNotificationConsumer;

    @jakarta.annotation.Resource
    private FlashOrderHandler flashOrderHandler;
    @jakarta.annotation.Resource
    private OrderTimeoutHandler orderTimeoutHandler;
    @jakarta.annotation.Resource
    private PaymentNotifyHandler paymentNotifyHandler;
    @jakarta.annotation.Resource
    private ChatDispatchHandler chatDispatchHandler;
    @jakarta.annotation.Resource
    private TeaNotificationHandler teaNotificationHandler;

    @Test
    void flashOrderListenerUsesSpringManagedDependencies() {
        flashOrderMqListener.onMessage("""
                {"transactionId":"flash:1:2:3:4","flashSaleId":1,"productId":2,"userId":3,"flashPrice":88.00}
                """);

        verify(flashOrderHandler).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void orderTimeoutConsumerUsesSpringManagedDependencies() {
        orderTimeoutConsumer.onMessage("{\"orderId\":11,\"userId\":22}");

        verify(orderTimeoutHandler).handle(11L);
    }

    @Test
    void paymentConsumerUsesSpringManagedDependencies() {
        paymentNotifyConsumer.onMessage("{\"orderId\":1,\"userId\":2,\"paymentId\":3}");

        verify(paymentNotifyHandler).handle(1L, 2L, 3L);
    }

    @Test
    void chatConsumerUsesSpringManagedDependencies() {
        chatMessageConsumer.onMessage("{\"receiverId\":10,\"messageId\":20,\"content\":\"hello\",\"msgType\":1}");

        verify(chatDispatchHandler).handle(10L, 20L, "hello", 1);
    }

    @Test
    void teaNotificationConsumerUsesSpringManagedDependencies() {
        teaNotificationConsumer.onMessage("{\"targetUserId\":7,\"type\":\"like\",\"sourceId\":8,\"actorId\":9}");

        verify(teaNotificationHandler).handle(7L, "like", 8L, 9L);
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        FlashOrderHandler flashOrderHandler() {
            return mock(FlashOrderHandler.class);
        }

        @Bean
        OrderTimeoutHandler orderTimeoutHandler() {
            return mock(OrderTimeoutHandler.class);
        }

        @Bean
        PaymentNotifyHandler paymentNotifyHandler() {
            return mock(PaymentNotifyHandler.class);
        }

        @Bean
        ChatDispatchHandler chatDispatchHandler() {
            return mock(ChatDispatchHandler.class);
        }

        @Bean
        TeaNotificationHandler teaNotificationHandler() {
            return mock(TeaNotificationHandler.class);
        }

        @Bean
        FlashOrderMqListener flashOrderMqListener(ObjectMapper objectMapper, FlashOrderHandler flashOrderHandler) {
            return new FlashOrderMqListener(objectMapper, flashOrderHandler);
        }

        @Bean
        OrderTimeoutConsumer orderTimeoutConsumer(ObjectMapper objectMapper, OrderTimeoutHandler orderTimeoutHandler) {
            return new OrderTimeoutConsumer(objectMapper, orderTimeoutHandler);
        }

        @Bean
        PaymentNotifyConsumer paymentNotifyConsumer(ObjectMapper objectMapper, PaymentNotifyHandler paymentNotifyHandler) {
            return new PaymentNotifyConsumer(objectMapper, paymentNotifyHandler);
        }

        @Bean
        ChatMessageConsumer chatMessageConsumer(ObjectMapper objectMapper, ChatDispatchHandler chatDispatchHandler) {
            return new ChatMessageConsumer(objectMapper, chatDispatchHandler);
        }

        @Bean
        TeaNotificationConsumer teaNotificationConsumer(ObjectMapper objectMapper, TeaNotificationHandler teaNotificationHandler) {
            return new TeaNotificationConsumer(objectMapper, teaNotificationHandler);
        }
    }
}
