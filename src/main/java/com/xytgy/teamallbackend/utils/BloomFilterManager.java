package com.xytgy.teamallbackend.utils;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.xytgy.teamallbackend.module.product.repository.ProductMapper;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

/**
 * 布隆过滤器管理器
 * <p>
 * 用于防止缓存穿透，快速判断数据是否可能存在
 * 误判率设置为1%，即可能存在1%的误判（将不存在的数据判断为存在）
 * 但不会将存在的数据判断为不存在（无漏判）
 */
@Slf4j
@Component
public class BloomFilterManager {

    private final ProductMapper productMapper;

    public BloomFilterManager(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    /**
     * 商品ID布隆过滤器
     * 预计插入100万个商品ID，误判率1%
     */
    private BloomFilter<String> productFilter;

    /**
     * 用户ID布隆过滤器
     * 预计插入100万个用户ID，误判率1%
     */
    private BloomFilter<String> userFilter;

    private static final double FPP = 0.01; // 误判率1%
    private static final int EXPECTED_INSERTIONS = 1_000_000; // 预计插入数量

    @PostConstruct
    public void init() {
        productFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                EXPECTED_INSERTIONS,
                FPP
        );

        userFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                EXPECTED_INSERTIONS,
                FPP
        );

        log.info("布隆过滤器初始化完成，预计插入数量: {}, 误判率: {}", EXPECTED_INSERTIONS, FPP);

        // P1#20: 启动时从 DB 全量加载商品 ID 到布隆过滤器
        // 解决多实例重启后布隆过滤器为空、误拦截合法请求的问题
        try {
            List<Long> productIds = productMapper.selectList(null)
                    .stream().map(p -> p.getId()).toList();
            for (Long id : productIds) {
                addProductId(id);
            }
            log.info("布隆过滤器初始化完成, 加载商品数={}", productIds.size());
        } catch (Exception e) {
            log.warn("布隆过滤器初始化失败, 将在运行时逐步填充: {}", e.getMessage());
        }
    }

    // ======================== 商品相关 ========================

    /**
     * 添加商品ID到布隆过滤器
     */
    public void addProductId(Long productId) {
        if (productId != null) {
            productFilter.put(String.valueOf(productId));
        }
    }

    /**
     * 批量添加商品ID到布隆过滤器
     */
    public void addProductIds(Collection<Long> productIds) {
        if (productIds != null) {
            productIds.forEach(this::addProductId);
        }
    }

    /**
     * 判断商品ID是否可能存在
     * <p>
     * 返回true表示可能存在（有1%误判率）
     * 返回false表示一定不存在
     */
    public boolean mightContainProduct(Long productId) {
        if (productId == null) {
            return false;
        }
        return productFilter.mightContain(String.valueOf(productId));
    }

    // ======================== 用户相关 ========================

    /**
     * 添加用户ID到布隆过滤器
     */
    public void addUserId(Long userId) {
        if (userId != null) {
            userFilter.put(String.valueOf(userId));
        }
    }

    /**
     * 批量添加用户ID到布隆过滤器
     */
    public void addUserIds(Collection<Long> userIds) {
        if (userIds != null) {
            userIds.forEach(this::addUserId);
        }
    }

    /**
     * 判断用户ID是否可能存在
     * <p>
     * 返回true表示可能存在（有1%误判率）
     * 返回false表示一定不存在
     */
    public boolean mightContainUser(Long userId) {
        if (userId == null) {
            return false;
        }
        return userFilter.mightContain(String.valueOf(userId));
    }

    // ======================== 通用方法 ========================

    /**
     * 清空所有布隆过滤器
     * <p>
     * 注意：布隆过滤器不支持删除操作，只能重新创建
     */
    public void clearAll() {
        productFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                EXPECTED_INSERTIONS,
                FPP
        );

        userFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                EXPECTED_INSERTIONS,
                FPP
        );

        log.info("布隆过滤器已清空");
    }

    /**
     * 获取商品布隆过滤器的预计插入数量
     */
    public long getExpectedProductInsertions() {
        return EXPECTED_INSERTIONS;
    }

    /**
     * 获取用户布隆过滤器的预计插入数量
     */
    public long getExpectedUserInsertions() {
        return EXPECTED_INSERTIONS;
    }
}
