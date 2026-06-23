package com.xytgy.teamallbackend.cache.browser;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

/**
 * 为浏览器协商缓存提供资源真实的最后修改时间。
 */
public interface LastModifiedProvider {

    Optional<Instant> getLastModified(Method method, Object[] args);
}
