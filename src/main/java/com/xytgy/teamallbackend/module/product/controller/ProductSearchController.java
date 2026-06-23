package com.xytgy.teamallbackend.module.product.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.module.product.dto.ProductSearchRequest;
import com.xytgy.teamallbackend.module.product.dto.ProductSearchResponse;
import com.xytgy.teamallbackend.module.product.service.ProductSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品搜索控制器。
 * <p>
 * 提供基于 Elasticsearch 的商品搜索 API，包括全文搜索、搜索建议和热门搜索。
 * 搜索接口无需登录即可访问（已在 SecurityConstants 中配置为公开路径）。
 */
@RestController
@RequestMapping("/api/product/search")
@Tag(name = "商品搜索")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class ProductSearchController {

    private final ProductSearchService productSearchService;

    /**
     * 全文搜索商品。
     * <p>
     * 支持按关键词、分类、品牌、产地、价格区间、标签等多条件组合搜索。
     * 搜索结果默认按综合排序（相关性 + 创建时间），也支持按销量、价格排序。
     *
     * @param keyword  搜索关键词
     * @param category 分类筛选
     * @param brand    品牌筛选
     * @param origin   产地筛选
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @param tag      标签筛选
     * @param sort     排序方式：default/sales/price_asc/price_desc
     * @param page     页码（从1开始）
     * @param pageSize 每页数量
     * @return 搜索结果（含商品列表、总数、分页信息）
     */
    @GetMapping
    @Operation(summary = "全文搜索商品", description = "支持中文分词、多条件组合搜索、结果高亮")
    public Result<ProductSearchResponse> search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "brand", required = false) String brand,
            @RequestParam(value = "origin", required = false) String origin,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "sort", defaultValue = "default") String sort,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {

        ProductSearchRequest request = new ProductSearchRequest();
        request.setKeyword(keyword);
        request.setCategory(category);
        request.setBrand(brand);
        request.setOrigin(origin);
        request.setMinPrice(minPrice);
        request.setMaxPrice(maxPrice);
        request.setTag(tag);
        request.setSort(sort);
        request.setPage(page);
        request.setPageSize(pageSize);

        return Result.success(productSearchService.search(request));
    }

    /**
     * 搜索建议/自动补全。
     * <p>
     * 根据用户输入的关键词返回相关商品名称建议，
     * 用于搜索框自动补全或"你是不是想搜"功能。
     *
     * @param keyword 用户输入的关键词前缀
     * @return 建议词列表（最多5个）
     */
    @GetMapping("/suggest")
    @Operation(summary = "搜索建议", description = "根据输入返回相关搜索建议词")
    public Result<List<String>> suggest(@RequestParam("keyword") String keyword) {
        return Result.success(productSearchService.getSuggestions(keyword));
    }

    /**
     * 热门搜索。
     * <p>
     * 返回当前热门搜索词列表，基于商品销量排名。
     *
     * @return 热门搜索词列表（最多10个）
     */
    @GetMapping("/hot")
    @Operation(summary = "热门搜索", description = "返回热门搜索关键词")
    public Result<List<String>> hotSearch() {
        return Result.success(productSearchService.getHotSearchKeywords());
    }
}
