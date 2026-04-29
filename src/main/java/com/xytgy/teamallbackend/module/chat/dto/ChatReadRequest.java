package com.xytgy.teamallbackend.module.chat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatReadRequest {
    @NotNull(message = "商家ID不能为空")
    private Long merchantId;
}