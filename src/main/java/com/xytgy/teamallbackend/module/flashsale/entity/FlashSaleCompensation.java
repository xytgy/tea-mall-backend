package com.xytgy.teamallbackend.module.flashsale.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("flash_sale_compensation")
public class FlashSaleCompensation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long failedOrderId;
    private Long userId;
    private Long flashSaleId;
    private String compensationType;
    private BigDecimal amount;
    private Integer status;
    private Long operatorId;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
