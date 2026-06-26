package com.xytgy.teamallbackend.module.flashsale.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FlashSaleStatusRequest {
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态值无效")
    @Max(value = 2, message = "状态值无效")
    private Integer status;
}
