package com.xytgy.teamallbackend.cache.bloom;

import com.google.common.hash.BloomFilter;
import com.xytgy.teamallbackend.module.product.mapper.ProductMapper;
import com.xytgy.teamallbackend.module.user.mapper.UserMapper;
import com.xytgy.teamallbackend.properties.CacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 从数据库分页构建布隆过滤器，并负责启动初始化与周期校准。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BloomFilterRebuildService {

    private final BloomFilterManager bloomFilterManager;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final CacheProperties cacheProperties;
    private final AtomicBoolean rebuilding = new AtomicBoolean(false);

    @Async("asyncExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void initializeAfterStartup() {
        rebuildAll();
    }

    @Scheduled(fixedDelayString = "${cache.bloom.rebuild-fixed-delay:1800000}")
    public void scheduledRebuild() {
        rebuildAll();
    }

    public void rebuildAll() {
        if (!rebuilding.compareAndSet(false, true)) {
            log.info("布隆过滤器重建任务正在执行，本次触发跳过");
            return;
        }
        try {
            rebuildProductFilter();
            rebuildUserFilter();
        } finally {
            rebuilding.set(false);
        }
    }

    void rebuildProductFilter() {
        long startNanos = System.nanoTime();
        try {
            BloomFilter<Long> newFilter = bloomFilterManager.newProductFilter();
            long count = loadIds(newFilter, productMapper::selectIdsAfter);
            bloomFilterManager.replaceProductFilter(newFilter);
            log.info("商品布隆过滤器重建完成, count={}, costMs={}",
                    count, elapsedMillis(startNanos));
        } catch (Exception e) {
            log.error("商品布隆过滤器重建失败，保留当前状态", e);
        }
    }

    void rebuildUserFilter() {
        long startNanos = System.nanoTime();
        try {
            BloomFilter<Long> newFilter = bloomFilterManager.newUserFilter();
            long count = loadIds(newFilter, userMapper::selectIdsAfter);
            bloomFilterManager.replaceUserFilter(newFilter);
            log.info("用户布隆过滤器重建完成, count={}, costMs={}",
                    count, elapsedMillis(startNanos));
        } catch (Exception e) {
            log.error("用户布隆过滤器重建失败，保留当前状态", e);
        }
    }

    private long loadIds(BloomFilter<Long> target, IdPageLoader loader) {
        int pageSize = cacheProperties.getBloom().getRebuildPageSize();
        long lastId = 0L;
        long total = 0L;
        while (true) {
            List<Long> ids = loader.load(lastId, pageSize);
            if (ids == null || ids.isEmpty()) {
                return total;
            }
            for (Long id : ids) {
                if (id == null || id <= lastId) {
                    throw new IllegalStateException("BloomFilter 游标分页结果必须严格递增");
                }
                target.put(id);
                lastId = id;
                total++;
            }
            if (ids.size() < pageSize) {
                return total;
            }
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    @FunctionalInterface
    private interface IdPageLoader {
        List<Long> load(long lastId, int limit);
    }
}
