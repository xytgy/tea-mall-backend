package com.xytgy.teamallbackend.module.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderPayRequest {
    @NotNull(message = "订单ID不能为空")
    private Long orderId;
    private String payMethod;
}
