package com.xytgy.teamallbackend.cache.browser;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

/**
 * 未配置真实更新时间时使用的空实现。
 */
public final class NoLastModifiedProvider implements LastModifiedProvider {

    @Override
    public Optional<Instant> getLastModified(Method method, Object[] args) {
        return Optional.empty();
    }
}
