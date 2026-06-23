package com.xytgy.teamallbackend.module.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品搜索响应 DTO。
 * <p>
 * 包含搜索命中的商品列表、总数、当前页码和每页数量，
 * 以及搜索建议（用于"你是不是想搜"功能）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchResponse {

    /**
     * 搜索命中的商品列表
     */
    private List<ProductSearchItem> items;

    /**
     * 命中总数
     */
    private long total;

    /**
     * 当前页码
     */
    private int page;

    /**
     * 每页数量
     */
    private int pageSize;

    /**
     * 搜索建议列表（相关搜索词）
     */
    private List<String> suggestions;

    /**
     * 搜索结果中的单个商品项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSearchItem {
        /**
         * 商品ID
         */
        private Long id;

        /**
         * 商品图片URL
         */
        private String imageUrl;

        /**
         * 商品名称（含高亮片段）
         */
        private String name;

        /**
         * 商品描述（含高亮片段）
         */
        private String description;

        /**
         * 分类
         */
        private String category;

        /**
         * 品牌
         */
        private String brand;

        /**
         * 产地
         */
        private String origin;

        /**
         * 价格
         */
        private BigDecimal price;

        /**
         * 销量
         */
        private Integer sales;
    }
}
