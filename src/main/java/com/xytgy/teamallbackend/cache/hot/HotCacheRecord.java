package com.xytgy.teamallbackend.cache.hot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Redis 中热点缓存的新存储结构。
 *
 * @param <T> 实际业务数据类型
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HotCacheRecord<T> {
    /** 真实业务数据。 */
    private T data;

    /** 显式表示当前记录是否为“缓存空值”。 */
    private boolean nullValue;

    /** 逻辑过期时间，Unix 毫秒时间戳。 */
    private long logicalExpireAt;

    /** 本次缓存记录的创建时间，Unix 毫秒时间戳。 */
    private long physicalCreatedAt;

    @JsonIgnore
    public boolean isLogicallyExpired(long now) {
        return now > logicalExpireAt;
    }
}
