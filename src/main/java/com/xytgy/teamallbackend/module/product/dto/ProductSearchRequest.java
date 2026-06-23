package com.xytgy.teamallbackend.module.product.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品搜索请求 DTO。
 * <p>
 * 支持全文关键词、分类、品牌、产地、价格区间、标签等多条件组合搜索，
 * 以及排序方式和分页参数。
 */
@Data
public class ProductSearchRequest {

    /**
     * 搜索关键词（匹配商品名称和描述）
     */
    private String keyword;

    /**
     * 分类筛选
     */
    private String category;

    /**
     * 品牌筛选
     */
    private String brand;

    /**
     * 产地筛选
     */
    private String origin;

    /**
     * 最低价格
     */
    private BigDecimal minPrice;

    /**
     * 最高价格
     */
    private BigDecimal maxPrice;

    /**
     * 标签筛选（如"绿茶"、"红茶"）
     */
    private String tag;

    /**
     * 排序方式：default(综合) / sales(销量) / price_asc(价格升序) / price_desc(价格降序)
     */
    private String sort;

    /**
     * 页码（从1开始）
     */
    private Integer page;

    /**
     * 每页数量
     */
    private Integer pageSize;
}
