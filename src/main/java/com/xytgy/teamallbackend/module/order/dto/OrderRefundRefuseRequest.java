package com.xytgy.teamallbackend.module.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OrderRefundRefuseRequest {
    @NotBlank(message = "拒绝原因不能为空")
    private String reason;
}