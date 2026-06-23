package com.xytgy.teamallbackend.mq.handler;

import com.xytgy.teamallbackend.config.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatDispatchHandler {

    private final ChatWebSocketHandler chatWebSocketHandler;

    public void handle(Long receiverId, Long messageId, String content, Integer msgType) {
        chatWebSocketHandler.dispatchToReceiver(receiverId, messageId, content, msgType);
    }
}
