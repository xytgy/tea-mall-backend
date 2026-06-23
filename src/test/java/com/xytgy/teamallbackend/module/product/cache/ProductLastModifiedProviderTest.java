package com.xytgy.teamallbackend.module.product.cache;

import com.xytgy.teamallbackend.module.product.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductLastModifiedProviderTest {

    @Mock
    private ProductMapper productMapper;

    private ProductLastModifiedProvider provider;
    private Method method;

    @BeforeEach
    void setUp() throws Exception {
        provider = new ProductLastModifiedProvider(productMapper);
        method = TestController.class.getMethod("detail", Long.class);
    }

    @Test
    void returnsProductUpdateTimeInBusinessTimeZone() {
        when(productMapper.selectActiveProductUpdateTime(1L))
                .thenReturn(LocalDateTime.of(2026, 6, 13, 11, 0));

        Optional<Instant> result = provider.getLastModified(method, new Object[]{1L});

        assertEquals(Optional.of(Instant.parse("2026-06-13T03:00:00Z")), result);
    }

    @Test
    void returnsEmptyWhenProductDoesNotExist() {
        when(productMapper.selectActiveProductUpdateTime(99L)).thenReturn(null);

        assertTrue(provider.getLastModified(method, new Object[]{99L}).isEmpty());
    }

    @Test
    void returnsEmptyWhenProductIdIsMissing() {
        assertTrue(provider.getLastModified(method, new Object[]{"invalid"}).isEmpty());
        verify(productMapper, never()).selectActiveProductUpdateTime(any());
    }

    static class TestController {
        public void detail(Long id) {
        }
    }
}
