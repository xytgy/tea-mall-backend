package com.xytgy.teamallbackend.module.product.cache;

import com.xytgy.teamallbackend.cache.browser.LastModifiedProvider;
import com.xytgy.teamallbackend.module.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 查询商品详情资源的真实更新时间。
 */
@Component
@RequiredArgsConstructor
public class ProductLastModifiedProvider implements LastModifiedProvider {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ProductMapper productMapper;

    @Override
    public Optional<Instant> getLastModified(Method method, Object[] args) {
        Long productId = extractProductId(args);
        if (productId == null) {
            return Optional.empty();
        }

        LocalDateTime updateTime = productMapper.selectActiveProductUpdateTime(productId);
        return updateTime == null
                ? Optional.empty()
                : Optional.of(updateTime.atZone(BUSINESS_ZONE).toInstant());
    }

    private Long extractProductId(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Long id) {
                return id;
            }
        }
        return null;
    }
}
