package com.xytgy.teamallbackend.module.flashsale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.mq.config.FlashSaleCacheManager;
import com.xytgy.teamallbackend.mq.message.flashsale.FlashOrderCreateMessage;
import com.xytgy.teamallbackend.mq.publisher.FlashOrderPublisher;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleBuyRequest;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSale;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleAuditLog;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleProduct;
import com.xytgy.teamallbackend.module.flashsale.mapper.FlashSaleAuditLogMapper;
import com.xytgy.teamallbackend.module.flashsale.mapper.FlashSaleMapper;
import com.xytgy.teamallbackend.module.flashsale.mapper.FlashSaleProductMapper;
import com.xytgy.teamallbackend.module.flashsale.service.FlashSaleService.FlashSaleBuyResult;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.order.mapper.OrdersMapper;
import com.xytgy.teamallbackend.module.product.entity.Product;
import com.xytgy.teamallbackend.module.product.mapper.ProductMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlashSaleCoreService {

    private final StringRedisTemplate stringRedisTemplate;
    private final FlashSaleCacheManager cacheManager;
    private final FlashSaleMapper flashSaleMapper;
    private final FlashSaleProductMapper flashSaleProductMapper;
    private final FlashSaleAuditLogMapper flashSaleAuditLogMapper;
    private final ProductMapper productMapper;
    private final OrdersMapper ordersMapper;
    private final FlashOrderPublisher flashOrderPublisher;
    private final FlashOrderPersistenceService flashOrderPersistenceService;
    private final FlashSaleRateLimiter rateLimiter;
    private final FlashSaleMetrics metrics;
    private final FlashSaleBehaviorAnalyzer behaviorAnalyzer;

    private DefaultRedisScript<Long> flashDeductScript;

    // 行为分析异步执行器（有界队列 + CallerRunsPolicy 防止极端场景 OOM）
    private static final ExecutorService BEHAVIOR_EXECUTOR =
            new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(1024),
                    r -> { Thread t = new Thread(r, "flash-behavior-async"); t.setDaemon(true); return t; },
                    new ThreadPoolExecutor.CallerRunsPolicy());

    // P0#1: 降级模式限流计数器，使用定时器每秒原子重置，消除读-改-写竞态
    private final AtomicInteger degradeCounter = new AtomicInteger(0);
        private static final int DEGRADE_MAX_PER_SEC = 100;
    private static final ScheduledExecutorService DEGRADE_RESETTER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "degrade-counter-reset");
                t.setDaemon(true);
                return t;
            });

    static final String CAPTCHA_TOKEN_PREFIX = "captcha_token:";
    static final String FLASH_STOCK_PREFIX = "{flash:";
    static final String FLASH_BOUGHT_PREFIX = "{flash:";

   private static final String KEY_DEDUCT_FAIL_LIMIT = "flash.deduct.fail.limit";
   private static final String KEY_DEDUCT_FAIL_SOLDOUT = "flash.deduct.fail.soldout";
    /** Lua 脚本返回值：已购买 */
    private static final long LUA_ALREADY_BOUGHT = -1L;
    /** Lua 脚本返回值：已售罄（扣减前库存即为 0） */
    private static final long LUA_SOLD_OUT = -2L;
    private static final String STOCK_SUFFIX = "}:stock";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_FLASH_PRICE = "flashPrice";
    private static final String FIELD_START_TIME = "startTime";
    private static final String FIELD_END_TIME = "endTime";

    // 信号值：Redis Lua 执行异常需降级到 MySQL
    private static final long DEDUCT_DEGRADE_SIGNAL = Long.MIN_VALUE;

    @PostConstruct
    public void init() {
        this.flashDeductScript = loadLuaScript("lua/flash_deduct.lua");
        // 每秒原子重置降级计数器
        DEGRADE_RESETTER.scheduleAtFixedRate(() -> degradeCounter.set(0), 0, 1, TimeUnit.SECONDS);
    }

    private DefaultRedisScript<Long> loadLuaScript(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            String text = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new DefaultRedisScript<>(text, Long.class);
        } catch (IOException e) {
            throw new ServiceException(ResultCode.ERROR, "加载 Lua 脚本失败: " + path);
        }
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "buyFallback")
    public FlashSaleBuyResult buy(Long userId, FlashSaleBuyRequest request) {
        metrics.increment("flash.deduct.total");

        Map<Object, Object> activity = loadActivity(request.getFlashSaleId());
        if (activity == null) { return fail("活动不存在"); }
        if (!"1".equals(String.valueOf(activity.get(FIELD_STATUS)))) {
            return fail("活动未进行中");
        }
        if (!isActivityTimeValid(activity)) {
            return fail("活动未开始或已结束");
        }

        Map<Object, Object> actProduct = loadActivityProduct(request.getFlashSaleId(), request.getProductId());
        if (actProduct == null) { return fail("活动商品不存在"); }

        FlashSaleBuyResult limitResult = checkRateLimitAndCaptcha(userId, request);
        if (limitResult != null) { return limitResult; }

        submitBehaviorAnalysis(userId, request);

        // 验证码 Token 一次性消费（GET + DEL 原子操作）
        String tokenKey = CAPTCHA_TOKEN_PREFIX + request.getCaptchaToken();
        String tokenValue = stringRedisTemplate.opsForValue().getAndDelete(tokenKey);
        if (tokenValue == null) {
            return fail("验证码无效或已过期");
        }

        // Lua 原子扣减（1 次 RTT = 限购检查 + 库存扣减 + 已购标记）
        Long deductResult = validateAndDecrementStock(userId, request.getProductId(), request.getFlashSaleId());
        if (deductResult == DEDUCT_DEGRADE_SIGNAL) {
            return degradeToMySql(request.getProductId(), userId, actProduct);
        }

        FlashSaleBuyResult errorResult = handleDeductResult(deductResult);
        if (errorResult != null) { return errorResult; }

        return processDeductSuccess(userId, request, actProduct, deductResult);
    }

    private FlashSaleBuyResult checkRateLimitAndCaptcha(Long userId, FlashSaleBuyRequest request) {
        FlashSaleRateLimiter.RateLimitResult rl = rateLimiter.check(userId, request.getFlashSaleId());
        if (rl.blocked()) {
            metrics.increment(KEY_DEDUCT_FAIL_LIMIT);
            return fail(rl.message());
        }
        if (rl.requireCaptcha()) {
            metrics.increment(KEY_DEDUCT_FAIL_LIMIT);
            return fail("请求过于频繁，请先完成验证码");
        }
        return null;
    }

    private void submitBehaviorAnalysis(Long userId, FlashSaleBuyRequest request) {
        final String deviceFp = request.getDeviceFingerprint();
        final Long fsId = request.getFlashSaleId();
        try {
            BEHAVIOR_EXECUTOR.execute(() -> {
                try {
                    behaviorAnalyzer.analyze(userId, fsId, deviceFp);
                } catch (Exception e) {
                    log.debug("异步行为分析失败: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            log.debug("行为分析提交失败: {}", e.getMessage());
        }
    }

    private FlashSaleBuyResult processDeductSuccess(Long userId, FlashSaleBuyRequest request,
                                                     Map<Object, Object> actProduct, Long deductResult) {
        metrics.increment("flash.deduct.success");
        cacheManager.syncLocalStock(request.getProductId(), deductResult.intValue());

        FlashOrderCreateMessage message = FlashOrderCreateMessage.builder()
                .transactionId(buildTransactionId(request.getFlashSaleId(), request.getProductId(), userId))
                .flashSaleId(request.getFlashSaleId())
                .productId(request.getProductId())
                .userId(userId)
                .flashPrice(new BigDecimal(String.valueOf(actProduct.get(FIELD_FLASH_PRICE))))
                .build();

        String pendingKey = "flash:pending:" + message.getTransactionId();
        try {
            stringRedisTemplate.opsForValue().set(pendingKey, message.getTransactionId(), 30, TimeUnit.MINUTES);
            boolean published = flashOrderPublisher.publishCreateOrder(message);
            if (!published) {
                log.warn("RocketMQ 不可用，秒杀订单转同步创建: transactionId={}", message.getTransactionId());
                flashOrderPersistenceService.createFlashOrder(message);
            }
        } catch (Exception mqEx) {
            log.error("MQ 发送或秒杀订单创建失败, pending 记录保留等待补偿: transactionId={}",
                    message.getTransactionId(), mqEx);
        }

        log.info("秒杀扣减成功: userId={}, productId={}, remainStock={}", userId, request.getProductId(), deductResult);
        return new FlashSaleBuyResult("SUCCESS", null, "抢购成功，订单生成中");
    }

    // Redis 熔断时返回友好提示
    public FlashSaleBuyResult buyFallback(Long userId, FlashSaleBuyRequest request, Throwable t) {
        log.warn("Redis 熔断器触发, userId={}, error={}", userId, t.getMessage());
        return new FlashSaleBuyResult("FAIL", null, "系统繁忙，请稍后重试");
    }

    /**
     * 本地库存预检 + Redis Lua 原子扣减。
     * <p>内部含 try-catch，Redis 异常时返回 DEDUCT_DEGRADE_SIGNAL 由调用方降级。
     *
     * @return 剩余库存（≥0）；{@link #DEDUCT_DEGRADE_SIGNAL} 表示需降级到 MySQL
     */
    private Long validateAndDecrementStock(Long userId, Long productId, Long flashSaleId) {
        // === P1-4: 删除冗余 SISMEMBER，完全依赖 Lua 内部原子检查 ===
        // Lua 脚本内部已包含 sismember 检查，结果 -1 表示已购买
        if (cacheManager.isLocalStockEmpty(productId)) {
            return LUA_SOLD_OUT;
        }
        String stockKey = FLASH_STOCK_PREFIX + productId + STOCK_SUFFIX;
        String boughtKey = FLASH_BOUGHT_PREFIX + productId + "}:bought:" + flashSaleId;
        try {
            return stringRedisTemplate.execute(
                    flashDeductScript, List.of(stockKey, boughtKey), String.valueOf(userId));
        } catch (Exception e) {
            log.warn("Redis Lua 执行失败, 降级到 MySQL: {}", e.getMessage());
            return DEDUCT_DEGRADE_SIGNAL;
        }
    }

    /**
     * 处理 Lua 脚本扣减结果，将原始返回值映射为业务结果。
     *
     * @param result Lua 脚本返回值：null=系统异常, -1=已达限购, -2=售罄, &gt;=0=剩余库存
     * @return 错误时返回 {@link FlashSaleBuyResult}，成功返回 null
     */
    private FlashSaleBuyResult handleDeductResult(Long result) {
        if (result == null) {
            metrics.increment(KEY_DEDUCT_FAIL_LIMIT);
            return fail("系统异常，请稍后重试");
        }
        if (result == LUA_ALREADY_BOUGHT) {
            metrics.increment("flash.deduct.fail.bought");
            return fail("已达限购数量，不可重复抢购");
        }
        if (result == LUA_SOLD_OUT) {
            metrics.increment(KEY_DEDUCT_FAIL_SOLDOUT);
            return fail("已售罄");
        }
        return null;
    }

    /**
     * 从缓存加载活动基础信息，缓存未命中时回源 DB 并写入缓存。
     *
     * @return 活动信息 Map，不存在时返回 null
     */
    private Map<Object, Object> loadActivity(Long flashSaleId) {
        Map<Object, Object> activity = cacheManager.getCachedActivity(flashSaleId);
        if (!activity.isEmpty()) {
            return activity;
        }
        FlashSale fs = flashSaleMapper.selectById(flashSaleId);
        if (fs == null) {
            return null;
        }
        Map<String, String> info = new LinkedHashMap<>();
        info.put("id", String.valueOf(fs.getId()));
        info.put(FIELD_START_TIME, String.valueOf(fs.getStartTime()));
        info.put(FIELD_END_TIME, String.valueOf(fs.getEndTime()));
        info.put(FIELD_STATUS, String.valueOf(fs.getStatus()));
        cacheManager.cacheActivity(fs.getId(), info);
        return new LinkedHashMap<>(info);
    }

    /**
     * 从缓存加载活动商品信息，缓存未命中时回源 DB 并写入缓存。
     *
     * @return 活动商品信息 Map，不存在时返回 null
     */
    private Map<Object, Object> loadActivityProduct(Long flashSaleId, Long productId) {
        Map<Object, Object> actProduct = cacheManager.getCachedActivityProduct(flashSaleId, productId);
        if (!actProduct.isEmpty()) {
            return actProduct;
        }
        FlashSaleProduct fsp = flashSaleProductMapper.selectOne(
                new LambdaQueryWrapper<FlashSaleProduct>()
                        .eq(FlashSaleProduct::getFlashSaleId, flashSaleId)
                        .eq(FlashSaleProduct::getProductId, productId));
        if (fsp == null) {
            return null;
        }
        Map<String, String> pinfo = new LinkedHashMap<>();
        pinfo.put(FIELD_FLASH_PRICE, String.valueOf(fsp.getFlashPrice()));
        pinfo.put("maxPerUser", String.valueOf(fsp.getMaxPerUser()));
        cacheManager.cacheActivityProduct(flashSaleId, productId, pinfo);
        return new LinkedHashMap<>(pinfo);
    }

    private boolean isActivityTimeValid(Map<Object, Object> activity) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = LocalDateTime.parse(String.valueOf(activity.get(FIELD_START_TIME)));
        LocalDateTime endTime = LocalDateTime.parse(String.valueOf(activity.get(FIELD_END_TIME)));
        return !now.isBefore(startTime) && !now.isAfter(endTime);
    }

    /**
     * Redis 不可用时降级到 MySQL 乐观锁扣减。
     * 使用本地 AtomicInteger 计数器限制每秒放行数，防止 DB 被打满。
     * 使用 UPDATE ... WHERE stock > 0 乐观锁，避免超卖。
     */
    private FlashSaleBuyResult degradeToMySql(Long productId, Long userId,
                                               Map<Object, Object> actProduct) {
        // P0#1: 定时器每秒重置，此处只做原子递增和阈值判断
        if (degradeCounter.incrementAndGet() > DEGRADE_MAX_PER_SEC) {
            metrics.increment(KEY_DEDUCT_FAIL_LIMIT);
            return fail("系统繁忙，请稍后重试");
        }

        // MySQL 乐观锁扣减：只有一行的 stock > 0 时才更新成功
        int affected = productMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Product>()
                        .eq("id", productId)
                        .gt("stock", 0)
                        .setSql("stock = stock - 1"));
        if (affected == 0) {
            metrics.increment(KEY_DEDUCT_FAIL_SOLDOUT);
            return fail("已售罄");
        }

        metrics.increment("flash.deduct.success");

        // 降级模式下直接创建订单（不走 MQ），避免 MQ 也故障时订单丢失
        try {
            Orders order = new Orders();
            // P1#14: 使用 UUID 避免订单号碰撞（原方案同毫秒+同尾缀会冲突）
            order.setOrderNo("FS" + UUID.randomUUID().toString().replace("-", ""));
            order.setUserId(userId);
            order.setTotalAmount(new BigDecimal(String.valueOf(actProduct.get(FIELD_FLASH_PRICE))));
            order.setStatus(0);
            order.setSource(1);
            order.setCreateTime(LocalDateTime.now());
            ordersMapper.insert(order);
        } catch (Exception e) {
            log.error("降级模式订单创建失败: {}", e.getMessage());
            // 降级模式下订单创建失败不回滚库存，事后对账修复
        }

        return new FlashSaleBuyResult("SUCCESS", null, "抢购成功（降级模式）");
    }

    public void warmup(Long flashSaleId, Long operatorId) {
        FlashSale flashSale = flashSaleMapper.selectById(flashSaleId);
        if (flashSale == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "活动不存在");
        }

        List<FlashSaleProduct> products = flashSaleProductMapper.selectList(
                new LambdaQueryWrapper<FlashSaleProduct>()
                        .eq(FlashSaleProduct::getFlashSaleId, flashSaleId));

        for (FlashSaleProduct fp : products) {
            int remaining = fp.getTotalStock() - fp.getSoldCount();
            if (remaining < 0) {
                remaining = 0;
            }
            String stockKey = FLASH_STOCK_PREFIX + fp.getProductId() + STOCK_SUFFIX;
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(remaining));
            cacheManager.warmUp(fp.getProductId(), remaining);
            cacheManager.broadcastStockChange(fp.getProductId(), remaining);
        }

        // 预热活动基础信息到 Redis Hash（消除 buy() 中的 DB 查询）
        Map<String, String> activityInfo = new LinkedHashMap<>();
        activityInfo.put("id", String.valueOf(flashSale.getId()));
        activityInfo.put(FIELD_START_TIME, String.valueOf(flashSale.getStartTime()));
        activityInfo.put(FIELD_END_TIME, String.valueOf(flashSale.getEndTime()));
        activityInfo.put(FIELD_STATUS, String.valueOf(flashSale.getStatus()));
        cacheManager.cacheActivity(flashSaleId, activityInfo);

        // 预热每个商品的活动信息（秒杀价、限购数）
        for (FlashSaleProduct fp : products) {
            Map<String, String> pinfo = new LinkedHashMap<>();
            pinfo.put(FIELD_FLASH_PRICE, String.valueOf(fp.getFlashPrice()));
            pinfo.put("maxPerUser", String.valueOf(fp.getMaxPerUser()));
            cacheManager.cacheActivityProduct(flashSaleId, fp.getProductId(), pinfo);
        }

        saveAuditLog(flashSaleId, operatorId, "WARMUP", "预热 " + products.size() + " 个商品库存");
        log.info("秒杀活动库存预热完成: flashSaleId={}, 商品数={}", flashSaleId, products.size());
    }

    public void restock(Long productId, Integer quantity, Long operatorId) {
        if (quantity == null || quantity <= 0) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "补货数量必须大于 0");
        }

        String stockKey = FLASH_STOCK_PREFIX + productId + STOCK_SUFFIX;
        String current = stringRedisTemplate.opsForValue().get(stockKey);
        int newStock = (current != null ? Integer.parseInt(current) : 0) + quantity;
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(newStock));
        cacheManager.syncLocalStock(productId, newStock);
        cacheManager.broadcastStockChange(productId, newStock);

        // 审计日志记录所有管理操作
        saveAuditLog(null, operatorId, "RESTOCK", "商品 " + productId + " 补货 " + quantity + " 件, 当前库存 " + newStock);
        log.info("秒杀商品补货: productId={}, 补货量={}, 当前库存={}", productId, quantity, newStock);
    }

    public void cleanup(Long flashSaleId, Long operatorId) {
        FlashSale flashSale = flashSaleMapper.selectById(flashSaleId);
        if (flashSale == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "活动不存在");
        }

        List<FlashSaleProduct> products = flashSaleProductMapper.selectList(
                new LambdaQueryWrapper<FlashSaleProduct>()
                        .eq(FlashSaleProduct::getFlashSaleId, flashSaleId));

        for (FlashSaleProduct fp : products) {
            String stockKey = FLASH_STOCK_PREFIX + fp.getProductId() + STOCK_SUFFIX;
            String boughtKey = FLASH_BOUGHT_PREFIX + fp.getProductId() + "}:bought:" + flashSaleId;
            stringRedisTemplate.delete(stockKey);
            stringRedisTemplate.delete(boughtKey);
            cacheManager.clear(fp.getProductId());
        }

        saveAuditLog(flashSaleId, operatorId, "CLEANUP", "清理 " + products.size() + " 个商品 Redis 数据");
        log.info("秒杀活动 Redis 数据清理完成: flashSaleId={}", flashSaleId);
    }

    private String buildTransactionId(Long flashSaleId, Long productId, Long userId) {
        return "flash:" + flashSaleId + ":" + productId + ":" + userId + ":" + System.currentTimeMillis();
    }

    FlashSaleBuyResult fail(String message) {
        return new FlashSaleBuyResult("FAIL", null, message);
    }

    private void saveAuditLog(Long flashSaleId, Long operatorId, String action, String detail) {
        FlashSaleAuditLog auditLog = new FlashSaleAuditLog();
        auditLog.setFlashSaleId(flashSaleId);
        auditLog.setOperatorId(operatorId);
        auditLog.setAction(action);
        auditLog.setDetail(detail);
        auditLog.setCreateTime(LocalDateTime.now());
        flashSaleAuditLogMapper.insert(auditLog);
    }
}
