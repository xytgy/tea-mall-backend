package com.xytgy.teamallbackend.module.product.dto;

import lombok.Data;

@Data
public class ProductAuditRequest {
    private Long id;
    private Integer status; // 1: 审核通过, 2: 驳回
}
