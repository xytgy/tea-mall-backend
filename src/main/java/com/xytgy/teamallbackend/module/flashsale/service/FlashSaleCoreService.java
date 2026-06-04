package com.xytgy.teamallbackend.module.flashsale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.config.mq.FlashSaleCacheManager;
import com.xytgy.teamallbackend.config.mq.MqConstants;
import com.xytgy.teamallbackend.config.mq.MqProducer;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleBuyRequest;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSale;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleAuditLog;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleProduct;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleAuditLogMapper;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleMapper;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleProductMapper;
import com.xytgy.teamallbackend.module.flashsale.service.FlashSaleService.FlashSaleBuyResult;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.order.repository.OrdersMapper;
import com.xytgy.teamallbackend.module.product.entity.Product;
import com.xytgy.teamallbackend.module.product.repository.ProductMapper;
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
    private final MqProducer mqProducer;
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
            throw new RuntimeException("加载 Lua 脚本失败: " + path, e);
        }
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "buyFallback")
    public FlashSaleBuyResult buy(Long userId, FlashSaleBuyRequest request) {
        metrics.increment("flash.deduct.total");

        // === P0-1: 从 Redis 缓存读取活动信息（0 次 DB 查询） ===
        // 预热时写入，24h TTL，缓存未命中才查 DB
        Map<Object, Object> activity = cacheManager.getCachedActivity(request.getFlashSaleId());
        if (activity.isEmpty()) {
            FlashSale fs = flashSaleMapper.selectById(request.getFlashSaleId());
            if (fs == null) { return fail("活动不存在"); }
            Map<String, String> info = new LinkedHashMap<>();
            info.put("id", String.valueOf(fs.getId()));
            info.put("startTime", String.valueOf(fs.getStartTime()));
            info.put("endTime", String.valueOf(fs.getEndTime()));
            info.put("status", String.valueOf(fs.getStatus()));
            cacheManager.cacheActivity(fs.getId(), info);
            activity = new LinkedHashMap<>(info);
        }

        if (!"1".equals(String.valueOf(activity.get("status")))) {
            return fail("活动未进行中");
        }

        // 活动时间校验
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = LocalDateTime.parse(String.valueOf(activity.get("startTime")));
        LocalDateTime endTime = LocalDateTime.parse(String.valueOf(activity.get("endTime")));
        if (now.isBefore(startTime) || now.isAfter(endTime)) {
            return fail("活动未开始或已结束");
        }

        // === P0-1: 从 Redis 缓存读取活动商品信息（0 次 DB 查询） ===
        Map<Object, Object> actProduct = cacheManager.getCachedActivityProduct(
                request.getFlashSaleId(), request.getProductId());
        if (actProduct.isEmpty()) {
            FlashSaleProduct fsp = flashSaleProductMapper.selectOne(
                    new LambdaQueryWrapper<FlashSaleProduct>()
                            .eq(FlashSaleProduct::getFlashSaleId, request.getFlashSaleId())
                            .eq(FlashSaleProduct::getProductId, request.getProductId()));
            if (fsp == null) { return fail("活动商品不存在"); }
            Map<String, String> pinfo = new LinkedHashMap<>();
            pinfo.put("flashPrice", String.valueOf(fsp.getFlashPrice()));
            pinfo.put("maxPerUser", String.valueOf(fsp.getMaxPerUser()));
            cacheManager.cacheActivityProduct(request.getFlashSaleId(), request.getProductId(), pinfo);
            actProduct = new LinkedHashMap<>(pinfo);
        }

        // L2 梯度限流
        FlashSaleRateLimiter.RateLimitResult rl = rateLimiter.check(userId, request.getFlashSaleId());
        if (rl.blocked()) {
            metrics.increment("flash.deduct.fail.limit");
            return fail(rl.message());
        }
        if (rl.requireCaptcha()) {
            metrics.increment("flash.deduct.fail.limit");
            return fail("请求过于频繁，请先完成验证码");
        }

        // === P1-5: 行为分析异步化（提交到线程池，不阻塞主链路） ===
        // 行为分析结果用于事后标记，不阻塞当前请求的扣减流程
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
            // 线程池满也不影响主流程
            log.debug("行为分析提交失败: {}", e.getMessage());
        }

        // 验证码 Token 一次性消费（GET + DEL 原子操作）
        String tokenKey = CAPTCHA_TOKEN_PREFIX + request.getCaptchaToken();
        String tokenValue = stringRedisTemplate.opsForValue().getAndDelete(tokenKey);
        if (tokenValue == null) {
            return fail("验证码无效或已过期");
        }

        // === P1-4: 删除冗余 SISMEMBER，完全依赖 Lua 内部原子检查 ===
        // 原来 buy() 先 SISMEMBER 再 Lua，两次操作之间存在 TOCTOU 竞态窗口
        // Lua 脚本内部已包含 sismember 检查，结果 -1 表示已购买
        if (cacheManager.isLocalStockEmpty(request.getProductId())) {
            metrics.increment("flash.deduct.fail.soldout");
            return fail("已售罄");
        }

        // Lua 原子扣减（1 次 RTT = 限购检查 + 库存扣减 + 已购标记）
        String stockKey = FLASH_STOCK_PREFIX + request.getProductId() + "}:stock";
        String boughtKey = FLASH_BOUGHT_PREFIX + request.getProductId() + "}:bought:" + request.getFlashSaleId();
        Long result;
        try {
            result = stringRedisTemplate.execute(
                    flashDeductScript,
                    List.of(stockKey, boughtKey),
                    String.valueOf(userId));
        } catch (Exception e) {
            // === P0-3: 降级修复 — 本地计数器限流 + DB 乐观锁防超卖 ===
            log.warn("Redis Lua 执行失败, 降级到 MySQL: {}", e.getMessage());
            return degradeToMySql(request.getProductId(), userId, request.getFlashSaleId(), actProduct);
        }

        if (result == null) {
            metrics.increment("flash.deduct.fail.limit");
            return fail("系统异常，请稍后重试");
        }
        if (result == -1L) {
            metrics.increment("flash.deduct.fail.bought");
            return fail("已达限购数量，不可重复抢购");
        }
        if (result == 0L) {
            metrics.increment("flash.deduct.fail.soldout");
            return fail("已售罄");
        }

        metrics.increment("flash.deduct.success");
        cacheManager.syncLocalStock(request.getProductId(), result.intValue());

        // === P1-6: MQ 消息发送前先写 pending 记录（防消息丢失） ===
        String transactionId = buildTransactionId(request.getFlashSaleId(), request.getProductId(), userId);
        Map<String, Object> payload = Map.of(
                "transactionId", transactionId,
                "flashSaleId", request.getFlashSaleId(),
                "productId", request.getProductId(),
                "userId", userId,
                "flashPrice", new BigDecimal(String.valueOf(actProduct.get("flashPrice"))));

        // 先写 pending 记录（TTL 30 分钟），Consumer 消费成功后删除
        // 如果 MQ 发送失败，pending 记录保留，定时补偿任务会据此重发
        String pendingKey = "flash:pending:" + transactionId;
        try {
            stringRedisTemplate.opsForValue().set(pendingKey, transactionId, 30, TimeUnit.MINUTES);
            mqProducer.send(MqConstants.TOPIC_FLASH_ORDER, MqConstants.TAG_FLASH_ORDER, transactionId, payload);
        } catch (Exception mqEx) {
            log.error("MQ 发送失败, pending 记录保留等待补偿: transactionId={}", transactionId);
            // 不影响用户体验，定时补偿任务会扫描 pending 表重发
        }

        log.info("秒杀扣减成功: userId={}, productId={}, remainStock={}", userId, request.getProductId(), result);
        return new FlashSaleBuyResult("SUCCESS", null, "抢购成功，订单生成中");
    }

    // Redis 熔断时返回友好提示
    public FlashSaleBuyResult buyFallback(Long userId, FlashSaleBuyRequest request, Throwable t) {
        log.warn("Redis 熔断器触发, userId={}, error={}", userId, t.getMessage());
        return new FlashSaleBuyResult("FAIL", null, "系统繁忙，请稍后重试");
    }

    /**
     * Redis 不可用时降级到 MySQL 乐观锁扣减。
     * 使用本地 AtomicInteger 计数器限制每秒放行数，防止 DB 被打满。
     * 使用 UPDATE ... WHERE stock > 0 乐观锁，避免超卖。
     */
    private FlashSaleBuyResult degradeToMySql(Long productId, Long userId, Long flashSaleId,
                                               Map<Object, Object> actProduct) {
        // P0#1: 定时器每秒重置，此处只做原子递增和阈值判断
        if (degradeCounter.incrementAndGet() > DEGRADE_MAX_PER_SEC) {
            metrics.increment("flash.deduct.fail.limit");
            return fail("系统繁忙，请稍后重试");
        }

        // MySQL 乐观锁扣减：只有一行的 stock > 0 时才更新成功
        int affected = productMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Product>()
                        .eq("id", productId)
                        .gt("stock", 0)
                        .setSql("stock = stock - 1"));
        if (affected == 0) {
            metrics.increment("flash.deduct.fail.soldout");
            return fail("已售罄");
        }

        metrics.increment("flash.deduct.success");

        // 降级模式下直接创建订单（不走 MQ），避免 MQ 也故障时订单丢失
        String transactionId = buildTransactionId(flashSaleId, productId, userId);
        try {
            Orders order = new Orders();
            // P1#14: 使用 UUID 避免订单号碰撞（原方案同毫秒+同尾缀会冲突）
            order.setOrderNo("FS" + UUID.randomUUID().toString().replace("-", ""));
            order.setUserId(userId);
            order.setTotalAmount(new BigDecimal(String.valueOf(actProduct.get("flashPrice"))));
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
            String stockKey = FLASH_STOCK_PREFIX + fp.getProductId() + "}:stock";
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(remaining));
            cacheManager.warmUp(fp.getProductId(), remaining);
            cacheManager.broadcastStockChange(fp.getProductId(), remaining);
        }

        // 预热活动基础信息到 Redis Hash（消除 buy() 中的 DB 查询）
        Map<String, String> activityInfo = new LinkedHashMap<>();
        activityInfo.put("id", String.valueOf(flashSale.getId()));
        activityInfo.put("startTime", String.valueOf(flashSale.getStartTime()));
        activityInfo.put("endTime", String.valueOf(flashSale.getEndTime()));
        activityInfo.put("status", String.valueOf(flashSale.getStatus()));
        cacheManager.cacheActivity(flashSaleId, activityInfo);

        // 预热每个商品的活动信息（秒杀价、限购数）
        for (FlashSaleProduct fp : products) {
            Map<String, String> pinfo = new LinkedHashMap<>();
            pinfo.put("flashPrice", String.valueOf(fp.getFlashPrice()));
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

        String stockKey = FLASH_STOCK_PREFIX + productId + "}:stock";
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
            String stockKey = FLASH_STOCK_PREFIX + fp.getProductId() + "}:stock";
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
