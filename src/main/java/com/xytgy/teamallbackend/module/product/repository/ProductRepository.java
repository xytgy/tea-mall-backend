package com.xytgy.teamallbackend.module.product.repository;

import com.xytgy.teamallbackend.module.product.document.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.math.BigDecimal;

/**
 * 商品 Elasticsearch 仓库接口。
 * <p>
 * 继承 ElasticsearchRepository 提供基本 CRUD，
 * 同时定义按名称/描述全文搜索、按分类搜索、按价格区间搜索等方法。
 * Spring Data 会根据方法名自动生成 ES 查询。
 */
public interface ProductRepository extends ElasticsearchRepository<ProductDocument, Long> {

    /**
     * 全文搜索：按名称或描述模糊匹配
     *
     * @param name        商品名称关键词
     * @param description 商品描述关键词
     * @param pageable    分页参数
     * @return 匹配的商品文档分页结果
     */
    Page<ProductDocument> findByNameOrDescriptionContaining(String name, String description, Pageable pageable);

    /**
     * 按分类精确搜索
     *
     * @param category 分类名称
     * @param pageable 分页参数
     * @return 该分类下的商品分页结果
     */
    Page<ProductDocument> findByCategory(String category, Pageable pageable);

    /**
     * 按价格区间搜索
     *
     * @param min      最低价格
     * @param max      最高价格
     * @param pageable 分页参数
     * @return 价格在区间内的商品分页结果
     */
    Page<ProductDocument> findByPriceBetween(BigDecimal min, BigDecimal max, Pageable pageable);
}
