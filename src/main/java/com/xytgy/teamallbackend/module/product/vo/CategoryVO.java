package com.xytgy.teamallbackend.module.product.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryVO {

    private Long id;

    private String name;

    private String icon;
}
