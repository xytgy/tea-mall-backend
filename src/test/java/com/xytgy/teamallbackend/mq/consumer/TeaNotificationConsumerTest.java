package com.xytgy.teamallbackend.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.mq.handler.TeaNotificationHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TeaNotificationConsumerTest {

    @Mock
    private TeaNotificationHandler teaNotificationHandler;

    @Test
    void dispatchesValidTeaNotificationToHandler() {
        TeaNotificationConsumer consumer = new TeaNotificationConsumer(new ObjectMapper(), teaNotificationHandler);

        consumer.onMessage("{\"targetUserId\":7,\"type\":\"like\",\"sourceId\":8,\"actorId\":9}");

        verify(teaNotificationHandler).handle(7L, "like", 8L, 9L);
    }

    @Test
    void rejectsIncompleteTeaNotification() {
        TeaNotificationConsumer consumer = new TeaNotificationConsumer(new ObjectMapper(), teaNotificationHandler);

        assertThrows(IllegalStateException.class, () -> consumer.onMessage("{\"targetUserId\":7,\"type\":\"like\"}"));
    }
}
