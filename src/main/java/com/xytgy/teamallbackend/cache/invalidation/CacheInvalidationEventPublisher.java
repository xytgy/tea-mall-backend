package com.xytgy.teamallbackend.cache.invalidation;

import com.xytgy.teamallbackend.cache.key.RedisGlobPattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 业务层缓存失效入口。事务存在时由监听器延迟到提交后执行。
 */
@Component
@RequiredArgsConstructor
public class CacheInvalidationEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void deleteKey(String key) {
        validate(key, "缓存 Key");
        eventPublisher.publishEvent(
                new CacheInvalidationEvent(CacheInvalidationEvent.Operation.KEY, key));
    }

    public void deletePattern(String pattern) {
        RedisGlobPattern.validate(pattern);
        eventPublisher.publishEvent(
                new CacheInvalidationEvent(CacheInvalidationEvent.Operation.PATTERN, pattern));
    }

    public void incrementVersion(String versionKey) {
        validate(versionKey, "版本 Key");
        eventPublisher.publishEvent(
                new CacheInvalidationEvent(CacheInvalidationEvent.Operation.VERSION, versionKey));
    }

    private static void validate(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + "不能为空");
        }
    }
}
