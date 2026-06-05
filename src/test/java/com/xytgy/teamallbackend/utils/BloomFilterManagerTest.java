package com.xytgy.teamallbackend.utils;

import com.xytgy.teamallbackend.cache.BloomFilterManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 布隆过滤器管理器单元测试
 * <p>
 * 测试布隆过滤器的核心功能：
 * 1. 添加和查询商品ID
 * 2. 添加和查询用户ID
 * 3. 批量添加功能
 * 4. 误判率验证
 *
 * @author TeaMall
 */
@SpringBootTest
class BloomFilterManagerTest {

    @Autowired
    private BloomFilterManager bloomFilterManager;

    /**
     * 每个测试方法执行前清空布隆过滤器
     */
    @BeforeEach
    void setUp() {
        bloomFilterManager.clearAll();
    }

    @Test
    @DisplayName("测试商品ID添加和查询")
    void testProductIdAddAndQuery() {
        // Given
        Long productId = 1001L;

        // When - 添加前查询，应该返回false
        boolean beforeAdd = bloomFilterManager.mightContainProduct(productId);

        // 添加商品ID
        bloomFilterManager.addProductId(productId);

        // 添加后查询，应该返回true
        boolean afterAdd = bloomFilterManager.mightContainProduct(productId);

        // Then
        assertFalse(beforeAdd, "添加前查询应该返回false");
        assertTrue(afterAdd, "添加后查询应该返回true");
    }

    @Test
    @DisplayName("测试用户ID添加和查询")
    void testUserIdAddAndQuery() {
        // Given
        Long userId = 2001L;

        // When - 添加前查询，应该返回false
        boolean beforeAdd = bloomFilterManager.mightContainUser(userId);

        // 添加用户ID
        bloomFilterManager.addUserId(userId);

        // 添加后查询，应该返回true
        boolean afterAdd = bloomFilterManager.mightContainUser(userId);

        // Then
        assertFalse(beforeAdd, "添加前查询应该返回false");
        assertTrue(afterAdd, "添加后查询应该返回true");
    }

    @Test
    @DisplayName("测试批量添加商品ID")
    void testBatchAddProductIds() {
        // Given
        List<Long> productIds = Arrays.asList(1001L, 1002L, 1003L, 1004L, 1005L);

        // When
        bloomFilterManager.addProductIds(productIds);

        // Then - 所有添加的ID都应该能查询到
        for (Long productId : productIds) {
            assertTrue(bloomFilterManager.mightContainProduct(productId),
                    "批量添加的商品ID应该能查询到: " + productId);
        }
    }

    @Test
    @DisplayName("测试批量添加用户ID")
    void testBatchAddUserIds() {
        // Given
        List<Long> userIds = Arrays.asList(2001L, 2002L, 2003L, 2004L, 2005L);

        // When
        bloomFilterManager.addUserIds(userIds);

        // Then - 所有添加的ID都应该能查询到
        for (Long userId : userIds) {
            assertTrue(bloomFilterManager.mightContainUser(userId),
                    "批量添加的用户ID应该能查询到: " + userId);
        }
    }

    @Test
    @DisplayName("测试空值处理")
    void testNullHandling() {
        // When & Then - 空值应该返回false
        assertFalse(bloomFilterManager.mightContainProduct(null),
                "查询null商品ID应该返回false");
        assertFalse(bloomFilterManager.mightContainUser(null),
                "查询null用户ID应该返回false");

        // 添加null不应该抛异常
        assertDoesNotThrow(() -> bloomFilterManager.addProductId(null),
                "添加null商品ID不应该抛异常");
        assertDoesNotThrow(() -> bloomFilterManager.addUserId(null),
                "添加null用户ID不应该抛异常");
    }

    @Test
    @DisplayName("测试未添加的ID查询")
    void testQueryNotAddedId() {
        // Given - 未添加的ID
        Long notAddedProductId = 99999L;
        Long notAddedUserId = 88888L;

        // When & Then - 未添加的ID应该返回false（无漏判）
        assertFalse(bloomFilterManager.mightContainProduct(notAddedProductId),
                "未添加的商品ID查询应该返回false");
        assertFalse(bloomFilterManager.mightContainUser(notAddedUserId),
                "未添加的用户ID查询应该返回false");
    }

    @Test
    @DisplayName("测试清空布隆过滤器")
    void testClearAll() {
        // Given - 添加一些数据
        bloomFilterManager.addProductId(1001L);
        bloomFilterManager.addUserId(2001L);

        // 验证数据存在
        assertTrue(bloomFilterManager.mightContainProduct(1001L));
        assertTrue(bloomFilterManager.mightContainUser(2001L));

        // When - 清空布隆过滤器
        bloomFilterManager.clearAll();

        // Then - 清空后查询应该返回false
        assertFalse(bloomFilterManager.mightContainProduct(1001L),
                "清空后商品ID查询应该返回false");
        assertFalse(bloomFilterManager.mightContainUser(2001L),
                "清空后用户ID查询应该返回false");
    }

    @Test
    @DisplayName("测试大批量数据添加")
    void testLargeBatchAdd() {
        // Given - 添加1000个商品ID
        int count = 1000;
        for (long i = 1; i <= count; i++) {
            bloomFilterManager.addProductId(i);
        }

        // When & Then - 所有添加的ID都应该能查询到
        int foundCount = 0;
        for (long i = 1; i <= count; i++) {
            if (bloomFilterManager.mightContainProduct(i)) {
                foundCount++;
            }
        }

        // 布隆过滤器不会有漏判，所以foundCount应该等于count
        assertEquals(count, foundCount, "所有添加的ID都应该能查询到");
    }

    @Test
    @DisplayName("测试误判率")
    void testFalsePositiveRate() {
        // Given - 添加10000个商品ID
        int addCount = 10000;
        for (long i = 1; i <= addCount; i++) {
            bloomFilterManager.addProductId(i);
        }

        // When - 查询10000个未添加的ID
        int queryCount = 10000;
        int falsePositiveCount = 0;
        for (long i = addCount + 1; i <= addCount + queryCount; i++) {
            if (bloomFilterManager.mightContainProduct(i)) {
                falsePositiveCount++;
            }
        }

        // Then - 误判率应该在1%左右（配置的FPP）
        double falsePositiveRate = (double) falsePositiveCount / queryCount;
        assertTrue(falsePositiveRate < 0.05,
                "误判率应该低于5%，实际为: " + falsePositiveRate);
    }

    @Test
    @DisplayName("测试商品ID和用户ID独立性")
    void testProductAndUserIdIndependence() {
        // Given
        Long id = 1001L;

        // When - 只添加商品ID
        bloomFilterManager.addProductId(id);

        // Then - 商品ID能查到，用户ID查不到
        assertTrue(bloomFilterManager.mightContainProduct(id),
                "添加的商品ID应该能查询到");
        assertFalse(bloomFilterManager.mightContainUser(id),
                "未添加的用户ID不应该能查询到");
    }
}
