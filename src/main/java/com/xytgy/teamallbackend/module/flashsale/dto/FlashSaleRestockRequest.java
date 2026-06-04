package com.xytgy.teamallbackend.module.flashsale.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FlashSaleRestockRequest {
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Min(value = 1, message = "补货数量必须大于0")
    private Integer quantity;
}
