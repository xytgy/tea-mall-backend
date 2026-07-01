package com.xytgy.teamallbackend.module.flashsale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "秒杀购买请求")
@Data
public class FlashSaleBuyRequest {
    @NotNull(message = "活动ID不能为空")
    @Positive(message = "秒杀活动ID不能为负数")
    private Long flashSaleId;

    @NotNull(message = "商品ID不能为空")
    @Positive(message = "ID不能为负数")
    private Long productId;

    @NotBlank(message = "图片验证码token不能为空")
    @Size(max = 64, message = "验证码长度不能超过64")
    private String captchaToken;

    /** 设备指纹,用于行为分析防刷 */
    @Size(max = 256, message = "设备指纹长度不能超过256")
    private String deviceFingerprint;
}
