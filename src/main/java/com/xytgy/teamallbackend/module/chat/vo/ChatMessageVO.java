package com.xytgy.teamallbackend.module.chat.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatMessageVO {
    private Long id;
    private Long sessionId;
    private Long senderId;
    private Long receiverId;
    private String content;
    private Integer msgType;
    private Integer isRead;
    private String createTime;
}