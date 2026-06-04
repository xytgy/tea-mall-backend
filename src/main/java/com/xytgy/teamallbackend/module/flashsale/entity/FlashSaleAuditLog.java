package com.xytgy.teamallbackend.module.flashsale.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("flash_sale_audit_log")
public class FlashSaleAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long flashSaleId;
    private Long operatorId;
    private String action;
    private String detail;
    private LocalDateTime createTime;
}
