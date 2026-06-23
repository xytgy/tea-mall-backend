package com.xytgy.teamallbackend.module.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xytgy.teamallbackend.module.product.document.ProductDocument;
import com.xytgy.teamallbackend.module.product.entity.Product;
import com.xytgy.teamallbackend.module.product.mapper.ProductMapper;
import com.xytgy.teamallbackend.module.product.repository.ProductRepository;
import com.xytgy.teamallbackend.cache.facade.RedisUtils;
import com.xytgy.teamallbackend.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品数据同步服务。
 * <p>
 * 负责 MySQL product 表与 Elasticsearch products 索引之间的数据同步。
 * 支持两种同步模式：
 * <ul>
 *   <li><b>全量同步</b>：将 MySQL 中所有已上架且已审核的商品同步到 ES，适用于初始化或数据修复</li>
 *   <li><b>增量同步</b>：商品增删改时实时同步单条数据到 ES，保持数据一致性</li>
 * </ul>
 * 全量同步通过 {@link Async} 异步执行，不阻塞主流程。
 * 同步过程中使用分布式锁防止重复执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class ProductSyncService {

    private final ProductMapper productMapper;
    private final ProductRepository productRepository;
    private final RedisUtils redisUtils;
    private final DistributedLock distributedLock;

    /** 同步批次大小 */
    private static final int BATCH_SIZE = 500;

    /** 全量同步最大重试次数 */
    private static final int MAX_RETRY = 3;

    /** 状态过期时间（分钟），全量同步可能耗时较长，需留足余量 */
    private static final long STATUS_TTL_MINUTES = 5;

    /** 全量同步分布式锁 key（多实例防重） */
    private static final String SYNC_LOCK_KEY = "es:product:sync:lock";

    private static final String SYNC_STATUS_KEY = "es:product:sync:status";

    /**
     * 全量同步：将 MySQL 中所有上架且审核通过的商品同步到 ES。
     * <p>
     * 异步执行，不阻塞调用方。使用分布式锁保证多实例不会重复执行。
     * 同步失败时自动重试（最多 {@link #MAX_RETRY} 次）。
     * 同步完成后清除搜索缓存。
     */
    @Async("asyncExecutor")
    public void fullSyncAsync() {
        if (!distributedLock.tryLock(SYNC_LOCK_KEY)) {
            log.warn("全量同步任务已在执行中，跳过本次请求");
            return;
        }

        long startTime = System.currentTimeMillis();
        log.info("========== 开始全量同步商品数据到 Elasticsearch ==========");
        redisUtils.set(SYNC_STATUS_KEY, "RUNNING", STATUS_TTL_MINUTES);

        try {
            int totalSynced = 0;
            Exception lastException = null;

            for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
                try {
                    totalSynced = doFullSync();
                    lastException = null;
                    break;
                } catch (Exception e) {
                    lastException = e;
                    log.error("全量同步第 {}/{} 次尝试失败", attempt, MAX_RETRY, e);
                    if (attempt < MAX_RETRY) {
                        Thread.sleep(2000L * attempt);
                    }
                }
            }

            if (lastException != null) {
                throw lastException;
            }

            redisUtils.invalidateVersion(ProductSearchService.CACHE_SEARCH_VERSION_KEY);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("========== 全量同步完成，共 {} 条商品，耗时 {} ms ==========", totalSynced, elapsed);
            redisUtils.set(SYNC_STATUS_KEY, "COMPLETED:" + totalSynced + ":" + elapsed + "ms", STATUS_TTL_MINUTES);

        } catch (Exception e) {
            log.error("全量同步商品数据到 ES 最终失败", e);
            redisUtils.set(SYNC_STATUS_KEY, "FAILED:" + MAX_RETRY + "次重试后失败:" + e.getMessage(), STATUS_TTL_MINUTES);
        } finally {
            distributedLock.unlock(SYNC_LOCK_KEY);
        }
    }

    private int doFullSync() {
        long lastId = 0;
        int totalSynced = 0;

        while (true) {
            LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                    .eq(Product::getStatus, 1)
                    .eq(Product::getAuditStatus, 1)
                    .gt(Product::getId, lastId)
                    .orderByAsc(Product::getId)
                    .last("LIMIT " + BATCH_SIZE);

            List<Product> products = productMapper.selectList(wrapper);
            if (products.isEmpty()) {
                break;
            }

            List<ProductDocument> documents = products.stream()
                    .map(this::toDocument)
                    .toList();

            productRepository.saveAll(documents);
            totalSynced += products.size();
            lastId = products.get(products.size() - 1).getId();

            log.info("全量同步进度: 已同步 {} 条", totalSynced);
        }

        return totalSynced;
    }

    /**
     * 增量同步：商品新增或更新时，同步单条数据到 ES。
     * <p>
     * 仅同步上架且审核通过的商品；下架或删除的商品会从 ES 中移除。
     *
     * @param product 变更的商品实体
     */
    public void syncProduct(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }

        try {
            if (product.getStatus() == 1 && product.getAuditStatus() == 1) {
                ProductDocument doc = toDocument(product);
                productRepository.save(doc);
                log.debug("增量同步商品到 ES: id={}", product.getId());
            } else {
                productRepository.deleteById(product.getId());
                log.debug("从 ES 移除商品: id={}", product.getId());
            }

            redisUtils.invalidateVersion(ProductSearchService.CACHE_SEARCH_VERSION_KEY);

        } catch (Exception e) {
            log.error("增量同步商品到 ES 失败, productId={}", product.getId(), e);
        }
    }

    /**
     * 增量同步：删除商品时，从 ES 中移除对应文档。
     *
     * @param productId 商品ID
     */
    public void deleteProduct(Long productId) {
        if (productId == null) {
            return;
        }

        try {
            productRepository.deleteById(productId);
            log.debug("从 ES 删除商品文档: id={}", productId);

            redisUtils.invalidateVersion(ProductSearchService.CACHE_SEARCH_VERSION_KEY);
        } catch (Exception e) {
            log.error("从 ES 删除商品文档失败, productId={}", productId, e);
        }
    }

    /**
     * 获取当前同步状态。
     *
     * @return 状态字符串：RUNNING / COMPLETED:数量:耗时 / FAILED:原因 / IDLE
     */
    public String getSyncStatus() {
        String status = redisUtils.get(SYNC_STATUS_KEY, String.class);
        return status != null ? status : "IDLE";
    }

    /**
     * 将 MySQL Product 实体转换为 ES ProductDocument。
     * <p>
     * 注意：Product 表中不存在 brand、origin、tags 字段，
     * 这些字段在 ES 文档中设为 null/空，后续可扩展数据库字段后在此处补充映射。
     */
    private ProductDocument toDocument(Product product) {
        return ProductDocument.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .category(product.getCategory())
                .imageUrl(product.getImageUrl())
                .brand(null)       // TODO: 待 product 表添加 brand 字段后映射
                .origin(null)      // TODO: 待 product 表添加 origin 字段后映射
                .price(product.getPrice())
                .sales(product.getSales() != null ? product.getSales() : 0)
                .createTime(product.getCreateTime())
                .tags(new String[0])        // TODO: 待 product 表添加 tags 字段后映射
                .build();
    }
}
