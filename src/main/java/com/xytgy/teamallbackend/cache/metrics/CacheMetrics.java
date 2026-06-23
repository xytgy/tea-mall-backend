package com.xytgy.teamallbackend.cache.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 缓存可观测性组件。
 *
 * <p>将 Micrometer 的计数器和计时器集中管理，缓存服务只需要表达
 * “发生了 L1 命中”或“执行一次回源”，不需要关心指标如何注册。
 *
 * <p>{@link MeterRegistry} 允许为空，便于未启用监控的环境和单元测试继续运行。
 */
@Component
public class CacheMetrics {

    private static final String CACHE_NAME_TAG = "cache.name";
    private static final String CACHE_NAME = "tea-mall";

    private final Counter l1Hit;
    private final Counter l1Miss;
    private final Counter l2Hit;
    private final Counter l2Miss;
    private final Counter hotLockContended;
    private final Counter hotLockAcquireSuccess;
    private final Counter hotLockAcquireFailure;
    private final Counter hotLockRenewFailure;
    private final Counter hotCachedNullHit;
    private final Counter hotStaleReturn;
    private final Counter hotWriteFailure;
    private final Counter hotDecodeFailure;
    private final Counter bloomBlocked;
    private final Counter invalidationFailure;
    private final Counter invalidationRetrySuccess;
    private final Counter invalidationRetryExhausted;
    private final Timer sourceLoad;

    public CacheMetrics(@Nullable MeterRegistry meterRegistry) {
        l1Hit = counter(meterRegistry, "cache.l1.hit", "L1 本地缓存命中次数");
        l1Miss = counter(meterRegistry, "cache.l1.miss", "L1 本地缓存未命中次数");
        l2Hit = counter(meterRegistry, "cache.l2.hit", "L2 Redis 缓存命中次数");
        l2Miss = counter(meterRegistry, "cache.l2.miss", "L2 Redis 缓存未命中次数");
        hotLockContended = counter(meterRegistry, "cache.hot.lock.contended", "热点数据分布式锁竞争失败次数");
        hotLockAcquireSuccess = counter(meterRegistry, "cache.hot.lock.acquire.success", "热点数据分布式锁获取成功次数");
        hotLockAcquireFailure = counter(meterRegistry, "cache.hot.lock.acquire.failure", "热点数据分布式锁获取失败次数");
        hotLockRenewFailure = counter(meterRegistry, "cache.hot.lock.renew.failure", "热点数据分布式锁续约失败次数");
        hotCachedNullHit = counter(meterRegistry, "cache.hot.null.hit", "热点缓存命中空值次数");
        hotStaleReturn = counter(meterRegistry, "cache.hot.stale.return", "热点缓存返回旧值次数");
        hotWriteFailure = counter(meterRegistry, "cache.hot.write.failure", "热点缓存写入失败次数");
        hotDecodeFailure = counter(meterRegistry, "cache.hot.decode.failure", "热点缓存反序列化失败次数");
        bloomBlocked = counter(meterRegistry, "cache.bloom.blocked", "布隆过滤器拦截次数");
        invalidationFailure = counter(meterRegistry, "cache.invalidation.failure", "缓存失效首次执行失败次数");
        invalidationRetrySuccess = counter(meterRegistry, "cache.invalidation.retry.success", "缓存失效重试成功次数");
        invalidationRetryExhausted = counter(meterRegistry, "cache.invalidation.retry.exhausted", "缓存失效重试耗尽次数");
        sourceLoad = meterRegistry == null ? null : Timer.builder("cache.source.load")
                .tag(CACHE_NAME_TAG, CACHE_NAME)
                .description("数据库回源加载耗时")
                .register(meterRegistry);
    }

    public void recordL1Hit() {
        increment(l1Hit);
    }

    public void recordL1Miss() {
        increment(l1Miss);
    }

    public void recordL2Hit() {
        increment(l2Hit);
    }

    public void recordL2Miss() {
        increment(l2Miss);
    }

    public void recordHotLockContended() {
        increment(hotLockContended);
    }

    public void recordHotLockAcquireSuccess() {
        increment(hotLockAcquireSuccess);
    }

    public void recordHotLockAcquireFailure() {
        increment(hotLockAcquireFailure);
    }

    public void recordHotCachedNullHit() {
        increment(hotCachedNullHit);
    }

    public void recordHotLockRenewFailure() {
        increment(hotLockRenewFailure);
    }

    public void recordHotStaleReturn() {
        increment(hotStaleReturn);
    }

    public void recordHotWriteFailure() {
        increment(hotWriteFailure);
    }

    public void recordHotDecodeFailure() {
        increment(hotDecodeFailure);
    }

    public void recordBloomBlocked() {
        increment(bloomBlocked);
    }

    public void recordInvalidationFailure() {
        increment(invalidationFailure);
    }

    public void recordInvalidationRetrySuccess() {
        increment(invalidationRetrySuccess);
    }

    public void recordInvalidationRetryExhausted() {
        increment(invalidationRetryExhausted);
    }

    public <T> T recordSourceLoad(Supplier<T> loader) {
        // Timer.record 会执行 loader，并记录本次数据库回源所花费的时间。
        return sourceLoad == null ? loader.get() : sourceLoad.record(loader::get);
    }

    private Counter counter(@Nullable MeterRegistry registry, String name, String description) {
        return registry == null ? null : Counter.builder(name)
                .tag(CACHE_NAME_TAG, CACHE_NAME)
                .description(description)
                .register(registry);
    }

    private void increment(@Nullable Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }
}
