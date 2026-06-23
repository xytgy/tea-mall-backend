package com.xytgy.teamallbackend.module.product.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品 Elasticsearch 文档实体。
 * <p>
 * 映射到 ES 中的 products 索引，用于全文搜索和多条件组合检索。
 * name 和 description 字段使用 ik 分词器以支持中文分词搜索。
 */
@Document(indexName = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {

    /**
     * 商品ID（与 MySQL product 表主键一致）
     */
    @Id
    private Long id;

    /**
     * 商品名称（ik_max_word 建索引时最大切分，ik_smart 搜索时智能切分）
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;

    /**
     * 商品描述（ik_max_word 建索引时最大切分，ik_smart 搜索时智能切分）
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String description;

    /**
     * 分类（精确匹配）
     */
    @Field(type = FieldType.Keyword)
    private String category;

    /**
     * 品牌（精确匹配）
     */
    @Field(type = FieldType.Keyword)
    private String brand;

    /**
     * 产地（精确匹配）
     */
    @Field(type = FieldType.Keyword)
    private String origin;

    /**
     * 价格（ScaledFloat 精确存储，scalingFactor=100 保留两位小数）
     */
    @Field(type = FieldType.Integer, scalingFactor = 100)
    private BigDecimal price;

    /**
     * 销量（用于热度排序）
     */
    @Field(type = FieldType.Integer)
    private Integer sales;

    /**
     * 创建时间
     */
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createTime;

    /**
     * 商品图片URL
     */
    @Field(type = FieldType.Keyword)
    private String imageUrl;

    /**
     * 标签数组（如绿茶、红茶、乌龙茶等，精确匹配）
     */
    @Builder.Default
    @Field(type = FieldType.Keyword)
    private String[] tags = new String[0];
}
