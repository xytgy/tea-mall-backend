package com.xytgy.teamallbackend.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 限流可观测性组件。
 *
 * <p>将限流相关的 Micrometer 指标集中管理，
 * 服务层只需要表达"发生了限流"或"发生了降级"。
 *
 * <p>{@link MeterRegistry} 允许为空，便于未启用监控的环境和单元测试继续运行。
 */
@Component
public class RateLimitMetrics {

    private final Counter accountRateLimited;
    private final Counter ipRateLimited;
    private final Counter accountLocked;
    private final Counter ipLocked;
    private final Counter accountReset;
    private final Counter redisFallback;

    public RateLimitMetrics(@Nullable MeterRegistry meterRegistry) {
        accountRateLimited = counter(meterRegistry, "ratelimit.account.limited", "账号维度被限流次数");
        ipRateLimited = counter(meterRegistry, "ratelimit.ip.limited", "IP 维度被限流次数");
        accountLocked = counter(meterRegistry, "ratelimit.account.locked", "账号锁定次数");
        ipLocked = counter(meterRegistry, "ratelimit.ip.locked", "IP 锁定次数");
        accountReset = counter(meterRegistry, "ratelimit.account.reset", "账号限流计数器重置次数");
        redisFallback = counter(meterRegistry, "ratelimit.redis.fallback", "Redis 不可用时 fail-open 降级次数");
    }

    public void recordAccountRateLimited() {
        increment(accountRateLimited);
    }

    public void recordIpRateLimited() {
        increment(ipRateLimited);
    }

    public void recordAccountLocked() {
        increment(accountLocked);
    }

    public void recordIpLocked() {
        increment(ipLocked);
    }

    public void recordAccountReset() {
        increment(accountReset);
    }

    public void recordRedisFallback() {
        increment(redisFallback);
    }

    private Counter counter(@Nullable MeterRegistry registry, String name, String description) {
        return registry == null ? null : Counter.builder(name)
                .description(description)
                .register(registry);
    }

    private void increment(@Nullable Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }
}
