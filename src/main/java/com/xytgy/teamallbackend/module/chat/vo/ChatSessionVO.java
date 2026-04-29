package com.xytgy.teamallbackend.module.chat.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatSessionVO {
    private Long buyerId;
    private String buyerName;
    private String buyerAvatar;
    private String lastMessage;
    private String lastTime;
    private Integer unreadCount;
}