package com.xytgy.teamallbackend.module.flashsale.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class FlashSaleCreateRequest {
    @NotBlank(message = "活动标题不能为空")
    private String title;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    private List<FlashSaleProductItem> products;

    @Data
    public static class FlashSaleProductItem {
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
}
