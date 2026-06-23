package com.xytgy.teamallbackend.cache.bloom;

import com.google.common.hash.BloomFilter;
import com.xytgy.teamallbackend.properties.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloomFilterManagerTest {

    private BloomFilterManager manager;

    @BeforeEach
    void setUp() {
        manager = new BloomFilterManager(testProperties());
    }

    @Test
    void shouldFailOpenBeforeInitialization() {
        assertTrue(manager.mightContainProduct(1001L));
        assertTrue(manager.mightContainUser(2001L));
        assertFalse(manager.mightContainProduct(null));
        assertFalse(manager.mightContainUser(0L));
    }

    @Test
    void shouldFilterUnknownIdsAfterReady() {
        BloomFilter<Long> productFilter = manager.newProductFilter();
        productFilter.put(1001L);
        manager.replaceProductFilter(productFilter);

        assertTrue(manager.mightContainProduct(1001L));
        assertFalse(manager.mightContainProduct(9999L));
    }

    @Test
    void shouldAddIdsInBatch() {
        manager.replaceProductFilter(manager.newProductFilter());
        manager.replaceUserFilter(manager.newUserFilter());

        manager.addProductIds(List.of(1L, 2L, 3L));
        manager.addUserIds(List.of(11L, 12L, 13L));

        assertTrue(manager.mightContainProduct(2L));
        assertTrue(manager.mightContainUser(12L));
    }

    @Test
    void shouldMergeIdsAddedDuringRebuildWhenReplacing() {
        BloomFilter<Long> rebuiltFilter = manager.newProductFilter();
        rebuiltFilter.put(1L);

        manager.addProductId(2L);
        manager.replaceProductFilter(rebuiltFilter);

        assertTrue(manager.mightContainProduct(1L));
        assertTrue(manager.mightContainProduct(2L));
    }

    @Test
    void clearShouldReturnToFailOpenState() {
        manager.replaceProductFilter(manager.newProductFilter());
        assertFalse(manager.mightContainProduct(123L));

        manager.clearAll();

        assertTrue(manager.mightContainProduct(123L));
    }

    private static CacheProperties testProperties() {
        CacheProperties properties = new CacheProperties();
        properties.getBloom().setProductExpectedInsertions(10_000);
        properties.getBloom().setUserExpectedInsertions(10_000);
        properties.getBloom().setFalsePositiveProbability(0.0001D);
        return properties;
    }
}
