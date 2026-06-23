package com.xytgy.teamallbackend.cache.local;

import com.xytgy.teamallbackend.cache.hot.HotCacheService;
import com.xytgy.teamallbackend.cache.key.RedisGlobPattern;
import com.xytgy.teamallbackend.cache.standard.MultiLevelCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 只负责清理当前应用实例中的两个 L1 缓存。
 */
@Component
@RequiredArgsConstructor
public class LocalCacheInvalidator {

    private final MultiLevelCacheService multiLevelCache;
    private final HotCacheService hotCache;

    public void invalidateKey(String key) {
        multiLevelCache.invalidateLocal(key);
        hotCache.invalidateLocal(key);
    }

    public void invalidatePattern(String redisPattern) {
        Pattern pattern = RedisGlobPattern.compile(redisPattern);
        multiLevelCache.invalidateLocalByPattern(pattern);
        hotCache.invalidateLocalByPattern(pattern);
    }
}
