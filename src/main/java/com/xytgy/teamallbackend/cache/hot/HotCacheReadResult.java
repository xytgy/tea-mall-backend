package com.xytgy.teamallbackend.cache.hot;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 热点缓存读取分类结果。
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class HotCacheReadResult<T> {
    private final boolean hit;
    private final boolean fresh;
    private final boolean nullValue;
    private final T data;
    private final long logicalExpireAt;
    private final long physicalCreatedAt;

    public static <T> HotCacheReadResult<T> miss() {
        return new HotCacheReadResult<>(false, false, false, null, 0L, 0L);
    }

    public static <T> HotCacheReadResult<T> fresh(T data, boolean nullValue,
                                                  long logicalExpireAt, long physicalCreatedAt) {
        return new HotCacheReadResult<>(true, true, nullValue, data, logicalExpireAt, physicalCreatedAt);
    }

    public static <T> HotCacheReadResult<T> stale(T data, boolean nullValue,
                                                  long logicalExpireAt, long physicalCreatedAt) {
        return new HotCacheReadResult<>(true, false, nullValue, data, logicalExpireAt, physicalCreatedAt);
    }
}
