package com.xytgy.teamallbackend.module.favorite.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class FavoriteItemVO {
    private Long id;
    private Long productId;
    private String name;
    private BigDecimal price;
    private String imageUrl;
}
