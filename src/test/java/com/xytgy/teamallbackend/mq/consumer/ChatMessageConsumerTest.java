package com.xytgy.teamallbackend.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.mq.handler.ChatDispatchHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatMessageConsumerTest {

    @Mock
    private ChatDispatchHandler chatDispatchHandler;

    @Test
    void dispatchesValidChatMessageToHandler() {
        ChatMessageConsumer consumer = new ChatMessageConsumer(new ObjectMapper(), chatDispatchHandler);

        consumer.onMessage("{\"receiverId\":10,\"messageId\":20,\"content\":\"hello\",\"msgType\":1}");

        verify(chatDispatchHandler).handle(10L, 20L, "hello", 1);
    }

    @Test
    void rejectsIncompleteChatMessage() {
        ChatMessageConsumer consumer = new ChatMessageConsumer(new ObjectMapper(), chatDispatchHandler);

        assertThrows(IllegalStateException.class, () -> consumer.onMessage("{\"receiverId\":10}"));
    }
}
