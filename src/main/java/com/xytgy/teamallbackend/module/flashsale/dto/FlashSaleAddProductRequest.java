package com.xytgy.teamallbackend.module.flashsale.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FlashSaleAddProductRequest {
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @NotNull(message = "秒杀价不能为空")
    @DecimalMin(value = "0.01", message = "秒杀价必须大于0")
    private BigDecimal flashPrice;

    @NotNull(message = "库存不能为空")
    @Min(value = 1, message = "库存必须大于0")
    private Integer totalStock;

    @Min(value = 1, message = "限购数必须大于0")
    private Integer maxPerUser = 1;
}
