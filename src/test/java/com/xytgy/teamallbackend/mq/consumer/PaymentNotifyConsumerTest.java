package com.xytgy.teamallbackend.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.mq.handler.PaymentNotifyHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentNotifyConsumerTest {

    @Mock
    private PaymentNotifyHandler paymentNotifyHandler;

    @Test
    void dispatchesValidPaymentMessageToHandler() {
        PaymentNotifyConsumer consumer = new PaymentNotifyConsumer(new ObjectMapper(), paymentNotifyHandler);

        consumer.onMessage("{\"orderId\":1,\"userId\":2,\"paymentId\":3}");

        verify(paymentNotifyHandler).handle(1L, 2L, 3L);
    }

    @Test
    void rejectsIncompletePaymentMessage() {
        PaymentNotifyConsumer consumer = new PaymentNotifyConsumer(new ObjectMapper(), paymentNotifyHandler);

        assertThrows(IllegalStateException.class, () -> consumer.onMessage("{\"orderId\":1}"));
    }
}
