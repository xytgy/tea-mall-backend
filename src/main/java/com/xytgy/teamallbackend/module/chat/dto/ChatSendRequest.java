package com.xytgy.teamallbackend.module.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatSendRequest {
    @NotNull(message = "商家ID不能为空")
    private Long merchantId;
    
    @NotBlank(message = "消息内容不能为空")
    private String content;
}