package com.xytgy.teamallbackend.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AuditVO {
    private Long id;
    private String name;
    private String merchant; // 商家名称
    private BigDecimal price;
    private LocalDateTime submitTime;
    private Integer status; // 审核状态 (0: 待审核, 1: 已通过, 2: 已驳回)
}
