package com.xytgy.teamallbackend.module.product.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xytgy.teamallbackend.module.product.document.ProductDocument;
import com.xytgy.teamallbackend.module.product.dto.ProductSearchRequest;
import com.xytgy.teamallbackend.module.product.dto.ProductSearchResponse;
import com.xytgy.teamallbackend.module.product.repository.ProductRepository;
import com.xytgy.teamallbackend.cache.facade.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品搜索服务。
 * <p>
 * 提供基于 Elasticsearch 的全文搜索能力，包括：
 * <ul>
 *   <li>全文搜索（支持中文分词，使用 ik 分词器）</li>
 *   <li>多条件组合搜索（分类、品牌、产地、价格区间、标签）</li>
 *   <li>搜索结果高亮显示</li>
 *   <li>搜索建议/相关搜索词</li>
 *   <li>多种排序方式（综合、销量、价格升降序）</li>
 * </ul>
 * 搜索结果通过 Redis 缓存加速，先查缓存再查 ES。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class ProductSearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductRepository productRepository;
    private final RedisUtils redisUtils;

    private static final String CACHE_SEARCH_PREFIX = "cache:product:search:";
    private static final String CACHE_SUGGEST_PREFIX = "cache:product:suggest:";
    private static final String CACHE_HOT_KEYWORDS_PREFIX = "cache:product:hot_keywords:";
    public static final String CACHE_SEARCH_VERSION_KEY = "cache:product:search:version";

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    /**
     * 全文搜索商品。
     * <p>
     * 支持多条件组合搜索，搜索结果缓存到 Redis（5分钟）。
     * 搜索关键词命中 name 或 description 时，返回高亮片段。
     *
     * @param request 搜索请求参数
     * @return 搜索响应（包含商品列表、总数、分页信息）
     */
    public ProductSearchResponse search(ProductSearchRequest request) {
        // 参数校验与默认值
        int page = (request.getPage() != null && request.getPage() > 0) ? request.getPage() : DEFAULT_PAGE;
        int pageSize = (request.getPageSize() != null && request.getPageSize() > 0)
                ? Math.min(request.getPageSize(), MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        // 生成缓存 key
        String cacheKey = redisUtils.versionedKey(
                CACHE_SEARCH_PREFIX, CACHE_SEARCH_VERSION_KEY,
                buildSearchSuffix(request, page, pageSize));
        return redisUtils.getOrLoad(cacheKey, new TypeReference<>() {}, 5, () -> doSearch(request, page, pageSize));
    }

    /**
     * 获取搜索建议（相关搜索词）。
     * <p>
     * 基于用户输入的关键词，从 ES 中提取 name 字段的 suggest 建议。
     * 结果缓存到 Redis（10分钟）。
     *
     * @param keyword 用户输入的关键词
     * @return 建议词列表（最多5个）
     */
    public List<String> getSuggestions(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }

        String cacheKey = redisUtils.versionedKey(
                CACHE_SUGGEST_PREFIX, CACHE_SEARCH_VERSION_KEY, keyword.trim());
        return redisUtils.getOrLoad(cacheKey, new TypeReference<>() {}, 10, () -> doSuggest(keyword.trim()));
    }

    /**
     * 获取热门搜索词。
     * <p>
     * 基于销量最高的商品名称返回热门搜索词列表。
     * 结果缓存到 Redis（30分钟）。
     *
     * @return 热门搜索词列表（最多10个）
     */
    public List<String> getHotSearchKeywords() {
        String cacheKey = redisUtils.versionedKey(
                CACHE_HOT_KEYWORDS_PREFIX, CACHE_SEARCH_VERSION_KEY, "top10");
        return redisUtils.getOrLoad(cacheKey, new TypeReference<>() {}, 30, () -> {
            Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "sales"));
            Page<ProductDocument> topProducts = productRepository.findAll(pageable);
            return topProducts.getContent().stream()
                    .map(ProductDocument::getName)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .limit(10)
                    .collect(Collectors.toList());
        });
    }

    // ======================== 内部搜索实现 ========================

    /**
     * 执行实际的 ES 搜索查询。
     * <p>
     * 构建 BoolQuery 组合多个过滤条件，使用 NativeQuery 执行搜索，
     * 对 name 和 description 字段启用高亮显示。
     */
    private ProductSearchResponse doSearch(ProductSearchRequest request, int page, int pageSize) {
        // 构建 Bool 查询
        var boolQuery = buildBoolQuery(request);

        // 构建分页和排序
        Pageable pageable = buildPageable(request.getSort(), page, pageSize);

        // 构建高亮
        var nameHighlightField = new HighlightField("name",
                HighlightFieldParameters.builder()
                        .withPreTags("<em>")
                        .withPostTags("</em>")
                        .build());
        var descHighlightField = new HighlightField("description",
                HighlightFieldParameters.builder()
                        .withPreTags("<em>")
                        .withPostTags("</em>")
                        .withNumberOfFragments(1)
                        .withFragmentSize(100)
                        .build());
        var highlight = new Highlight(List.of(nameHighlightField, descHighlightField));
        var highlightQuery = new HighlightQuery(highlight, ProductDocument.class);

        // 构建 NativeQuery
        NativeQuery nativeQuery = new NativeQueryBuilder()
                .withQuery(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.bool(boolQuery)))
                .withPageable(pageable)
                .withHighlightQuery(highlightQuery)
                .build();

        // 执行搜索
        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);

        // 组装结果
        List<ProductSearchResponse.ProductSearchItem> items = searchHits.getSearchHits().stream()
                .map(this::toSearchItem)
                .collect(Collectors.toList());

        return ProductSearchResponse.builder()
                .items(items)
                .total(searchHits.getTotalHits())
                .page(page)
                .pageSize(pageSize)
                .suggestions(List.of())
                .build();
    }

    /**
     * 构建 BoolQuery：组合 keyword、category、brand、origin、price、tag 等过滤条件。
     */
    private BoolQuery buildBoolQuery(ProductSearchRequest request) {
        var boolBuilder = new BoolQuery.Builder();

        // 关键词搜索：匹配 name 或 description
        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            boolBuilder.must(m -> m.multiMatch(mm -> mm
                    .fields("name^3", "description")  // name 权重更高
                    .query(keyword)
                    .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                    .fuzziness("AUTO")
            ));
        }

        // 分类过滤
        if (StringUtils.hasText(request.getCategory())) {
            boolBuilder.filter(f -> f.term(t -> t.field("category").value(request.getCategory().trim())));
        }

        // 品牌过滤
        if (StringUtils.hasText(request.getBrand())) {
            boolBuilder.filter(f -> f.term(t -> t.field("brand").value(request.getBrand().trim())));
        }

        // 产地过滤
        if (StringUtils.hasText(request.getOrigin())) {
            boolBuilder.filter(f -> f.term(t -> t.field("origin").value(request.getOrigin().trim())));
        }

        // 价格区间过滤
        if (request.getMinPrice() != null || request.getMaxPrice() != null) {
            boolBuilder.filter(f -> f.range(r -> {
                r.field("price");
                if (request.getMinPrice() != null) {
                    r.gte(co.elastic.clients.json.JsonData.of(request.getMinPrice()));
                }
                if (request.getMaxPrice() != null) {
                    r.lte(co.elastic.clients.json.JsonData.of(request.getMaxPrice()));
                }
                return r;
            }));
        }

        // 标签过滤
        if (StringUtils.hasText(request.getTag())) {
            boolBuilder.filter(f -> f.term(t -> t.field("tags").value(request.getTag().trim())));
        }

        return boolBuilder.build();
    }

    /**
     * 根据排序参数构建 Pageable。
     */
    private Pageable buildPageable(String sort, int page, int pageSize) {
        Sort springSort;
        switch (sort == null ? "default" : sort) {
            case "sales" -> springSort = Sort.by(Sort.Direction.DESC, "sales");
            case "price_asc" -> springSort = Sort.by(Sort.Direction.ASC, "price");
            case "price_desc" -> springSort = Sort.by(Sort.Direction.DESC, "price");
            default -> springSort = Sort.by(Sort.Direction.DESC, "_score").and(Sort.by(Sort.Direction.DESC, "createTime"));
        }
        return PageRequest.of(page - 1, pageSize, springSort);
    }

    /**
     * 将 ES SearchHit 转换为搜索结果项。
     * 优先使用高亮片段，无高亮时使用原始字段值。
     */
    private ProductSearchResponse.ProductSearchItem toSearchItem(SearchHit<ProductDocument> hit) {
        ProductDocument doc = hit.getContent();
        var highlights = hit.getHighlightFields();

        // 取高亮片段，没有则取原始值
        String name = getHighlightOrOriginal(highlights, "name", doc.getName());
        String description = getHighlightOrOriginal(highlights, "description", doc.getDescription());

        return ProductSearchResponse.ProductSearchItem.builder()
                .id(doc.getId())
                .imageUrl(doc.getImageUrl())
                .name(name)
                .description(description)
                .category(doc.getCategory())
                .brand(doc.getBrand())
                .origin(doc.getOrigin())
                .price(doc.getPrice())
                .sales(doc.getSales())
                .build();
    }

    /**
     * 从高亮字段中获取第一个片段，若无高亮则返回原始值。
     */
    private String getHighlightOrOriginal(
            java.util.Map<String, List<String>> highlights, String field, String original) {
        List<String> fragments = highlights.get(field);
        if (fragments != null && !fragments.isEmpty()) {
            return fragments.get(0);
        }
        return original;
    }

    /**
     * 搜索建议实现：使用 prefix 查询从 ES 中获取相似商品名称。
     */
    private List<String> doSuggest(String keyword) {
        var prefixQuery = co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q
                .multiMatch(mm -> mm
                        .fields("name^2", "description")
                        .query(keyword)
                        .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BoolPrefix)
                )
        );

        NativeQuery nativeQuery = new NativeQueryBuilder()
                .withQuery(prefixQuery)
                .withPageable(PageRequest.of(0, 5))
                .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);
        return hits.getSearchHits().stream()
                .map(hit -> hit.getContent().getName())
                .filter(StringUtils::hasText)
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * 生成搜索缓存 key，基于请求参数的 MD5 哈希。
     */
    private String buildSearchSuffix(ProductSearchRequest request, int page, int pageSize) {
        String raw = String.join(":",
                request.getKeyword() == null ? "" : request.getKeyword(),
                request.getCategory() == null ? "" : request.getCategory(),
                request.getBrand() == null ? "" : request.getBrand(),
                request.getOrigin() == null ? "" : request.getOrigin(),
                request.getMinPrice() == null ? "" : request.getMinPrice().toPlainString(),
                request.getMaxPrice() == null ? "" : request.getMaxPrice().toPlainString(),
                request.getTag() == null ? "" : request.getTag(),
                request.getSort() == null ? "" : request.getSort(),
                String.valueOf(page),
                String.valueOf(pageSize)
        );
        return md5Hex(raw);
    }

    private static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
