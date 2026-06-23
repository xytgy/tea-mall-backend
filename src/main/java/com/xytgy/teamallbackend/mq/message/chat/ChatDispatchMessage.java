package com.xytgy.teamallbackend.mq.message.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatDispatchMessage {

    private Long senderId;
    private Long receiverId;
    private Long messageId;
    private String content;
    private Integer msgType;
}
