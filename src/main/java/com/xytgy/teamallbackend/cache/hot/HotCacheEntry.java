package com.xytgy.teamallbackend.cache.hot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Redis 中热点缓存的存储结构。
 *
 * @param <T> 实际业务数据类型
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HotCacheEntry<T> {
    /** 真实业务数据。 */
    private T data;

    /** 逻辑过期时间，使用 Unix 毫秒时间戳。 */
    private long expireAt;

    /**
     * 判断业务数据是否已经逻辑过期。
     * 该计算属性使用 {@link JsonIgnore}，不会被写入 Redis JSON。
     */
    @JsonIgnore
    public boolean isLogicallyExpired() {
        return System.currentTimeMillis() > expireAt;
    }
}
