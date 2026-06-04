package com.xytgy.teamallbackend.module.flashsale.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FlashSaleBuyRequest {
    @NotNull(message = "活动ID不能为空")
    private Long flashSaleId;

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    private String captchaToken;

    /** 设备指纹（前端 Canvas 指纹 + UA 拼接，未授权时传 IP+UA），用于 L4 行为分析 */
    private String deviceFingerprint;
}
