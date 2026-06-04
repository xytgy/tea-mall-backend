package com.xytgy.teamallbackend.module.flashsale.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 秒杀纯内存计数器，定时持久化到日志。
 * <p>
 * 使用 LongAdder 替代 AtomicLong，在高并发写场景下吞吐量更高。
 * 纯 JDK 实现，不依赖外部监控框架。
 */
@Component
@Slf4j
public class FlashSaleMetrics {

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();

    /** 扣减相关 */
    public static final String DEDUCT_TOTAL = "flash.deduct.total";
    public static final String DEDUCT_SUCCESS = "flash.deduct.success";
    public static final String DEDUCT_FAIL_SOLDOUT = "flash.deduct.fail.soldout";
    public static final String DEDUCT_FAIL_BOUGHT = "flash.deduct.fail.bought";
    public static final String DEDUCT_FAIL_LIMIT = "flash.deduct.fail.limit";

    /** 订单相关 */
    public static final String ORDER_CREATE_TOTAL = "flash.order.create.total";
    public static final String ORDER_CREATE_SUCCESS = "flash.order.create.success";
    public static final String ORDER_CREATE_FAIL = "flash.order.create.fail";

    /** 回补相关 */
    public static final String REFUND_TOTAL = "flash.refund.total";
    public static final String REFUND_SUCCESS = "flash.refund.success";

    /** 对账 & 申诉 */
    public static final String RECONCILE_ANOMALY = "flash.reconcile.anomaly";
    public static final String APPEAL_TOTAL = "flash.appeal.total";
    public static final String APPEAL_SUCCESS = "flash.appeal.success";

    public void increment(String counterName) {
        counters.computeIfAbsent(counterName, k -> new LongAdder()).increment();
    }

    public void increment(String counterName, long delta) {
        counters.computeIfAbsent(counterName, k -> new LongAdder()).add(delta);
    }

    public long getCount(String counterName) {
        LongAdder adder = counters.get(counterName);
        return adder != null ? adder.sum() : 0;
    }

    /**
     * 返回所有计数器的不可变快照
     */
    public Map<String, Long> snapshot() {
        Map<String, Long> snap = new HashMap<>(counters.size());
        counters.forEach((key, adder) -> snap.put(key, adder.sum()));
        return Map.copyOf(snap);
    }

    /**
     * 重置所有计数器（用于周期性统计后归零）
     */
    public void reset() {
        counters.clear();
    }

    /**
     * 每分钟输出一次指标摘要到日志
     */
    @Scheduled(fixedRate = 60000)
    public void reportMetrics() {
        Map<String, Long> snap = snapshot();
        if (snap.isEmpty()) {
            return;
        }
        log.info("[FlashSaleMetrics] {}", snap);
    }
}
