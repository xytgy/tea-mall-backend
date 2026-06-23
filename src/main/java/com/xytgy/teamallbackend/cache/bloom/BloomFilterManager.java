package com.xytgy.teamallbackend.cache.bloom;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.xytgy.teamallbackend.properties.CacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 管理商品和用户的 JVM 本地布隆过滤器。
 *
 * <p>未完成初始化时采用 fail-open：返回“可能存在”，由后续数据库查询确认，
 * 避免空过滤器把真实数据错误拦截。重建成功后再切换到正常过滤模式。</p>
 */
@Slf4j
@Component
public class BloomFilterManager {

    private final CacheProperties.Bloom properties;
    private final AtomicReference<FilterState> productState;
    private final AtomicReference<FilterState> userState;
    private final ReentrantLock productMutationLock = new ReentrantLock();
    private final ReentrantLock userMutationLock = new ReentrantLock();
    private final Set<Long> pendingProductIds = new HashSet<>();
    private final Set<Long> pendingUserIds = new HashSet<>();

    public BloomFilterManager(CacheProperties cacheProperties) {
        this.properties = cacheProperties.getBloom();
        validateProperties(properties);
        this.productState = new AtomicReference<>(FilterState.notReady(newProductFilter()));
        this.userState = new AtomicReference<>(FilterState.notReady(newUserFilter()));
    }

    public void addProductId(Long productId) {
        addId(productId, productState, productMutationLock, pendingProductIds);
    }

    public void addProductIds(Collection<Long> productIds) {
        if (productIds != null) {
            productIds.forEach(this::addProductId);
        }
    }

    public boolean mightContainProduct(Long productId) {
        return mightContain(productId, productState);
    }

    public void addUserId(Long userId) {
        addId(userId, userState, userMutationLock, pendingUserIds);
    }

    public void addUserIds(Collection<Long> userIds) {
        if (userIds != null) {
            userIds.forEach(this::addUserId);
        }
    }

    public boolean mightContainUser(Long userId) {
        return mightContain(userId, userState);
    }

    /**
     * 清空后进入未就绪状态。在下一次重建成功前，非空 ID 都会放行。
     */
    public void clearAll() {
        productMutationLock.lock();
        userMutationLock.lock();
        try {
            pendingProductIds.clear();
            pendingUserIds.clear();
            productState.set(FilterState.notReady(newProductFilter()));
            userState.set(FilterState.notReady(newUserFilter()));
        } finally {
            userMutationLock.unlock();
            productMutationLock.unlock();
        }
        log.info("布隆过滤器已清空并切换为 fail-open 状态");
    }

    public long getExpectedProductInsertions() {
        return properties.getProductExpectedInsertions();
    }

    public long getExpectedUserInsertions() {
        return properties.getUserExpectedInsertions();
    }

    BloomFilter<Long> newProductFilter() {
        return createFilter(properties.getProductExpectedInsertions());
    }

    BloomFilter<Long> newUserFilter() {
        return createFilter(properties.getUserExpectedInsertions());
    }

    void replaceProductFilter(BloomFilter<Long> rebuiltFilter) {
        replaceFilter(rebuiltFilter, productState, productMutationLock, pendingProductIds);
    }

    void replaceUserFilter(BloomFilter<Long> rebuiltFilter) {
        replaceFilter(rebuiltFilter, userState, userMutationLock, pendingUserIds);
    }

    boolean isProductReady() {
        return productState.get().ready();
    }

    boolean isUserReady() {
        return userState.get().ready();
    }

    private void addId(Long id,
                       AtomicReference<FilterState> stateRef,
                       ReentrantLock lock,
                       Set<Long> pendingIds) {
        if (id == null || id <= 0) {
            return;
        }
        lock.lock();
        try {
            pendingIds.add(id);
            stateRef.get().filter().put(id);
        } finally {
            lock.unlock();
        }
    }

    private boolean mightContain(Long id, AtomicReference<FilterState> stateRef) {
        if (id == null || id <= 0) {
            return false;
        }
        FilterState state = stateRef.get();
        return !state.ready() || state.filter().mightContain(id);
    }

    private void replaceFilter(BloomFilter<Long> rebuiltFilter,
                               AtomicReference<FilterState> stateRef,
                               ReentrantLock lock,
                               Set<Long> pendingIds) {
        if (rebuiltFilter == null) {
            throw new IllegalArgumentException("rebuiltFilter 不能为空");
        }
        lock.lock();
        try {
            pendingIds.forEach(rebuiltFilter::put);
            stateRef.set(FilterState.ready(rebuiltFilter));
            pendingIds.clear();
        } finally {
            lock.unlock();
        }
    }

    private BloomFilter<Long> createFilter(long expectedInsertions) {
        return BloomFilter.create(
                Funnels.longFunnel(),
                expectedInsertions,
                properties.getFalsePositiveProbability()
        );
    }

    private static void validateProperties(CacheProperties.Bloom properties) {
        if (properties.getProductExpectedInsertions() <= 0
                || properties.getUserExpectedInsertions() <= 0) {
            throw new IllegalArgumentException("BloomFilter 预计插入数量必须大于 0");
        }
        double fpp = properties.getFalsePositiveProbability();
        if (fpp <= 0 || fpp >= 1) {
            throw new IllegalArgumentException("BloomFilter 误判率必须在 0 和 1 之间");
        }
        if (properties.getRebuildPageSize() <= 0) {
            throw new IllegalArgumentException("BloomFilter 重建分页大小必须大于 0");
        }
    }

    private record FilterState(BloomFilter<Long> filter, boolean ready) {
        private static FilterState ready(BloomFilter<Long> filter) {
            return new FilterState(filter, true);
        }

        private static FilterState notReady(BloomFilter<Long> filter) {
            return new FilterState(filter, false);
        }
    }
}
