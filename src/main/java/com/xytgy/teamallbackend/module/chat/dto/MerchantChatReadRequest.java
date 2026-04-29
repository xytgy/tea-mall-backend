package com.xytgy.teamallbackend.module.chat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MerchantChatReadRequest {
    @NotNull(message = "买家ID不能为空")
    private Long buyerId;
}