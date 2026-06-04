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
    public static final String ORDER_CREATE_TOTAL =