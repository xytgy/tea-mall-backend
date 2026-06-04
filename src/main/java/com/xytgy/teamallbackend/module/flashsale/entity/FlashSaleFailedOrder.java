package com.xytgy.teamallbackend.module.flashsale.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("flash_sale_failed_order")
public class FlashSaleFailedOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String transactionId;
    private Long userId;
    private Long productId;
    private Long flashSaleId;
    private String errorMsg;
    private Integer retryCount;
    private Integer status;
    private LocalDateTime createTime;
}
