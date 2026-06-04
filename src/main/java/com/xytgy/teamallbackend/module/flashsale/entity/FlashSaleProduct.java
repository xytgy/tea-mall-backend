package com.xytgy.teamallbackend.module.flashsale.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("flash_sale_product")
public class FlashSaleProduct {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long flashSaleId;
    private Long productId;
    private BigDecimal flashPrice;
    private Integer totalStock;
    private Integer soldCount;
    private Integer maxPerUser;
    private LocalDateTime createTime;
}
