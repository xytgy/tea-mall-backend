package com.xytgy.teamallbackend.cache.bloom;

import com.xytgy.teamallbackend.module.product.mapper.ProductMapper;
import com.xytgy.teamallbackend.module.user.mapper.UserMapper;
import com.xytgy.teamallbackend.properties.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BloomFilterRebuildServiceTest {

    private ProductMapper productMapper;
    private UserMapper userMapper;
    private BloomFilterManager manager;
    private BloomFilterRebuildService service;

    @BeforeEach
    void setUp() {
        CacheProperties properties = new CacheProperties();
        properties.getBloom().setProductExpectedInsertions(10_000);
        properties.getBloom().setUserExpectedInsertions(10_000);
        properties.getBloom().setFalsePositiveProbability(0.0001D);
        properties.getBloom().setRebuildPageSize(2);
        manager = new BloomFilterManager(properties);
        productMapper = mock(ProductMapper.class);
        userMapper = mock(UserMapper.class);
        service = new BloomFilterRebuildService(manager, productMapper, userMapper, properties);
    }

    @Test
    void shouldRebuildBothFiltersWithCursorPaging() {
        when(productMapper.selectIdsAfter(0L, 2)).thenReturn(List.of(1L, 2L));
        when(productMapper.selectIdsAfter(2L, 2)).thenReturn(List.of(3L));
        when(userMapper.selectIdsAfter(0L, 2)).thenReturn(List.of(11L, 12L));
        when(userMapper.selectIdsAfter(12L, 2)).thenReturn(List.of());

        service.rebuildAll();

        assertTrue(manager.isProductReady());
        assertTrue(manager.isUserReady());
        assertTrue(manager.mightContainProduct(3L));
        assertTrue(manager.mightContainUser(12L));
        assertFalse(manager.mightContainProduct(999L));
    }

    @Test
    void productFailureShouldNotPreventUserRebuild() {
        when(productMapper.selectIdsAfter(0L, 2)).thenThrow(new RuntimeException("db error"));
        when(userMapper.selectIdsAfter(0L, 2)).thenReturn(List.of(11L));

        service.rebuildAll();

        assertFalse(manager.isProductReady());
        assertTrue(manager.mightContainProduct(999L));
        assertTrue(manager.isUserReady());
        assertTrue(manager.mightContainUser(11L));
    }
}
