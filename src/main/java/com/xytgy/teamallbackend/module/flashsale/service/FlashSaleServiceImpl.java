package com.xytgy.teamallbackend.module.flashsale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.config.mq.FlashSaleCacheManager;
import com.xytgy.teamallbackend.config.mq.MqConstants;
import com.xytgy.teamallbackend.config.mq.MqProducer;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.flashsale.dto.CaptchaVerifyRequest;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleBuyRequest;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSale;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleAuditLog;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleCompensation;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleFailedOrder;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleProduct;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleWhitelist;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleAuditLogMapper;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleCompensationMapper;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleFailedOrderMapper;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleMapper;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleProductMapper;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleWhitelistMapper;
import com.xytgy.teamallbackend.module.flashsale.vo.FlashSaleProductVO;
import com.xytgy.teamallbackend.module.flashsale.vo.FlashSaleVO;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.order.repository.OrdersMapper;
import com.xytgy.teamallbackend.module.product.entity.Product;
import com.xytgy.teamallbackend.module.product.repository.ProductMapper;
import com.xytgy.teamallbackend.utils.RedisUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlashSaleServiceImpl implements FlashSaleService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisUtils redisUtils;
    private final FlashSaleCacheManager cacheManager;
    private final FlashSaleMapper flashSaleMapper;
    private final FlashSaleProductMapper flashSaleProductMapper;
    private final FlashSaleWhitelistMapper flashSaleWhitelistMapper;
    private final FlashSaleAuditLogMapper flashSaleAuditLogMapper;
    private final ProductMapper productMapper;
    private final OrdersMapper ordersMapper;
    private final MqProducer mqProducer;
    private final FlashSaleRateLimiter rateLimiter;
    private final FlashSaleMetrics metrics;
    private final FlashSaleBehaviorAnalyzer behaviorAnalyzer;
    private final FlashSaleFailedOrderMapper failedOrderMapper;
    private final FlashSaleCompensationMapper compensationMapper;
    private final CaptchaPool captchaPool;

    private DefaultRedisScript<Long> flashDeductScript;

    // P2#10: 有界队列(1000) + CallerRunsPolicy，极端流量下背压传导而非 OOM
    private static final java.util.concurrent.ExecutorService BEHAVIOR_EXECUTOR =
            new java.util.concurrent.ThreadPoolExecutor(2, 2, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.LinkedBlockingQueue<>(1000),
                    r -> {
                        Thread t = new Thread(r, "flash-behavior-async");
                        t.setDaemon(true);
                        return t;
                    },
                    new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

    // P0#1: 降级模式限流计数器，使用定时器每秒原子重置，消除读-改-写竞态
    private final java.util.concurrent.atomic.AtomicInteger degradeCounter = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final int DEGRADE_MAX_PER_SEC = 100;
    private static final java.util.concurrent.ScheduledExecutorService DEGRADE_RESETTER =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "degrade-counter-reset");
                t.setDaemon(true);
                return t;
            });

    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final String CAPTCHA_TOKEN_PREFIX = "captcha_token:";
    private static final String FLASH_STOCK_PREFIX = "{flash:";
    private static final String FLASH_BOUGHT_PREFIX = "{flash:";
    private static final String FLASH_PENDING_PREFIX = "flash:pending:";

    @PostConstruct
    public void init() {
        this.flashDeductScript = loadLuaScript("lua/flash_deduct.lua");
        // 每秒原子重置降级计数器
        DEGRADE_RESETTER.scheduleAtFixedRate(() -> degradeCounter.set(0), 0, 1, java.util.concurrent.TimeUnit.SECONDS);
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

    @Override
    public List<FlashSaleVO> listActiveSales() {
        LocalDateTime now = LocalDateTime.now();
        List<FlashSale> sales = flashSaleMapper.selectList(
                new LambdaQueryWrapper<FlashSale>()
                        .eq(FlashSale::getStatus, 1)
                        .le(FlashSale::getStartTime, now)
                        .ge(FlashSale::getEndTime, now)
        );
        return sales.stream().map(this::toFlashSaleVO).toList();
    }

    @Override
    public List<FlashSaleProductVO> getProducts(Long flashSaleId) {
        List<FlashSaleProduct> products = flashSaleProductMapper.selectList(
                new LambdaQueryWrapper<FlashSaleProduct>()
                        .eq(FlashSaleProduct::getFlashSaleId, flashSaleId)
        );
        if (products.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> productIds = products.stream().map(FlashSaleProduct::getProductId).toList();
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return products.stream().map(fp -> buildProductVO(fp, productMap.get(fp.getProductId()))).toList();
    }

    private FlashSaleProductVO buildProductVO(FlashSaleProduct fp, Product product) {
        FlashSaleProductVO vo = new FlashSaleProductVO();
        vo.setId(fp.getId());
        vo.setProductId(fp.getProductId());
        vo.setFlashPrice(fp.getFlashPrice());
        vo.setTotalStock(fp.getTotalStock());
        vo.setMaxPerUser(fp.getMaxPerUser());

        if (product != null) {
            vo.setProductName(product.getName());
            vo.setProductImage(product.getImageUrl());
            vo.setOriginalPrice(product.getPrice());
        }

        String stockKey = FLASH_STOCK_PREFIX + fp.getProductId() + "}:stock";
        String stockStr = stringRedisTemplate.opsForValue().get(stockKey);
        int remaining = stockStr != null ? Integer.parseInt(stockStr) : fp.getTotalStock();
        vo.setRemainingStock(remaining);
        return vo;
    }

    @Override
    public CaptchaResult generateCaptcha(Long userId) {
        String uuid = UUID.randomUUID().toString();
        String code;
        String imageBase64;

        // 优先从预生成池获取（零开销），池空时降级为实时生成
        CaptchaPool.CaptchaEntry entry = captchaPool.poll();
        if (entry != null) {
            code = entry.code();
            imageBase64 = entry.imageBase64();
        } else {
            log.warn("验证码池已空，降级为实时生成");
            code = generateRandomCode();
            imageBase64 = renderCaptchaImage(code);
        }

        stringRedisTemplate.opsForValue().set(
                CAPTCHA_PREFIX + uuid, code, 60, TimeUnit.SECONDS);

        return new CaptchaResult(uuid, imageBase64);
    }

    private String generateRandomCode() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private String renderCaptchaImage(String code) {
        int width = 120;
        int height = 40;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        g.setColor(Color.LIGHT_GRAY);
        for (int i = 0; i < 6; i++) {
            g.drawLine(
                    random.nextInt(width), random.nextInt(height),
                    random.nextInt(width), random.nextInt(height));
        }

        g.setFont(new Font(Font.DIALOG, Font.BOLD, 28));
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(random.nextInt(150), random.nextInt(150), random.nextInt(150)));
            g.drawString(String.valueOf(code.charAt(i)), 20 + i * 25, 30);
        }
        g.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new ServiceException(ResultCode.ERROR, "验证码图片生成失败");
        }
    }

    @Override
    public String verifyCaptcha(Long userId, CaptchaVerifyRequest request) {
        String captchaKey = CAPTCHA_PREFIX + request.getUuid();
        String cachedCode = stringRedisTemplate.opsForValue().get(captchaKey);

        if (cachedCode == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "验证码已过期");
        }
        if (!cachedCode.equalsIgnoreCase(request.getCode())) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "验证码错误");
        }

        // 验证通过后立即删除，防止重放
        stringRedisTemplate.delete(captchaKey);

        // 生成秒杀 token，15 秒有效期，用于 buy 方法一次性校验
        String token = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(
                CAPTCHA_TOKEN_PREFIX + token, String.valueOf(userId), 15, TimeUnit.SECONDS);

        return token;
    }

    @Override
    public FlashSaleBuyResult buy(Long userId, FlashSaleBuyRequest request) {
        metrics.increment("flash.deduct.total");

        // === P0-1: 从 Redis 缓存读取活动信息（0 次 DB 查询） ===
        // 预热时写入，24h TTL，缓存未命中才查 DB
        Map<Object, Object> activity = cacheManager.getCachedActivity(request.getFlashSaleId());
        if (activity.isEmpty()) {
            FlashSale fs = flashSaleMapper.selectById(request.getFlashSaleId());
            if (fs == null) { return fail("活动不存在"); }
            Map<String, String> info = new java.util.LinkedHashMap<>();
            info.put("id", String.valueOf(fs.getId()));
            info.put("startTime", String.valueOf(fs.getStartTime()));
            info.put("endTime", String.valueOf(fs.getEndTime()));
            info.put("status", String.valueOf(fs.getStatus()));
            cacheManager.cacheActivity(fs.getId(), info);
            activity = new java.util.LinkedHashMap<>(info);
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
            Map<String, String> pinfo = new java.util.LinkedHashMap<>();
            pinfo.put("flashPrice", String.valueOf(fsp.getFlashPrice()));
            pinfo.put("maxPerUser", String.valueOf(fsp.getMaxPerUser()));
            cacheManager.cacheActivityProduct(request.getFlashSaleId(), request.getProductId(), pinfo);
            actProduct = new java.util.LinkedHashMap<>(pinfo);
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
                "flashPrice", new java.math.BigDecimal(String.valueOf(actProduct.get("flashPrice"))));

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
        int affected = productMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Product>()
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
            order.setOrderNo("FS" + java.util.UUID.randomUUID().toString().replace("-", ""));
            order.setUserId(userId);
            order.setTotalAmount(new java.math.BigDecimal(String.valueOf(actProduct.get("flashPrice"))));
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

    @Override
    public Map<String, Object> getOrderResult(Long userId, Long orderId) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (orderId != null) {
            Orders order = ordersMapper.selectById(orderId);
            if (order != null && order.getUserId().equals(userId)) {
                result.put("orderId", order.getId());
                result.put("orderNo", order.getOrderNo());
                result.put("status", order.getStatus());
                result.put("message", getOrderStatusMessage(order.getStatus()));
                return result;
            }
            result.put("message", "订单不存在");
            return result;
        }

        Set<String> keys = stringRedisTemplate.keys(FLASH_PENDING_PREFIX + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            for (String key : keys) {
                String pendingOrderId = stringRedisTemplate.opsForValue().get(key);
                if (pendingOrderId != null) {
                    result.put("orderId", Long.parseLong(pendingOrderId));
                    result.put("status", "PENDING");
                    result.put("message", "订单处理中");
                    return result;
                }
            }
        }

        result.put("message", "暂无抢购记录");
        return result;
    }

    @Override
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
        Map<String, String> activityInfo = new java.util.LinkedHashMap<>();
        activityInfo.put("id", String.valueOf(flashSale.getId()));
        activityInfo.put("startTime", String.valueOf(flashSale.getStartTime()));
        activityInfo.put("endTime", String.valueOf(flashSale.getEndTime()));
        activityInfo.put("status", String.valueOf(flashSale.getStatus()));
        cacheManager.cacheActivity(flashSaleId, activityInfo);

        // 预热每个商品的活动信息（秒杀价、限购数）
        for (FlashSaleProduct fp : products) {
            Map<String, String> pinfo = new java.util.LinkedHashMap<>();
            pinfo.put("flashPrice", String.valueOf(fp.getFlashPrice()));
            pinfo.put("maxPerUser", String.valueOf(fp.getMaxPerUser()));
            cacheManager.cacheActivityProduct(flashSaleId, fp.getProductId(), pinfo);
        }

        saveAuditLog(flashSaleId, operatorId, "WARMUP", "预热 " + products.size() + " 个商品库存");
        log.info("秒杀活动库存预热完成: flashSaleId={}, 商品数={}", flashSaleId, products.size());
    }

    @Override
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

    @Override
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

    @Override
    public void addWhitelist(Long flashSaleId, List<Long> userIds, Long operatorId) {
        FlashSale flashSale = flashSaleMapper.selectById(flashSaleId);
        if (flashSale == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "活动不存在");
        }

        for (Long uid : userIds) {
            long exists = flashSaleWhitelistMapper.selectCount(
                    new LambdaQueryWrapper<FlashSaleWhitelist>()
                            .eq(FlashSaleWhitelist::getFlashSaleId, flashSaleId)
                            .eq(FlashSaleWhitelist::getUserId, uid));
            if (exists == 0) {
                FlashSaleWhitelist wl = new FlashSaleWhitelist();
                wl.setFlashSaleId(flashSaleId);
                wl.setUserId(uid);
                wl.setCreateTime(LocalDateTime.now());
                flashSaleWhitelistMapper.insert(wl);
            }
        }

        // 同步白名单到 Redis Set（限流器从 Redis 读取，不再查 DB）
        rateLimiter.syncWhitelistToRedis(flashSaleId, userIds);

        saveAuditLog(flashSaleId, operatorId, "WHITELIST", "添加 " + userIds.size() + " 个白名单用户");
        log.info("秒杀白名单添加完成: flashSaleId={}, 用户数={}", flashSaleId, userIds.size());
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

    private String buildTransactionId(Long flashSaleId, Long productId, Long userId) {
        return "flash:" + flashSaleId + ":" + productId + ":" + userId + ":" + System.currentTimeMillis();
    }

    private FlashSaleBuyResult fail(String message) {
        return new FlashSaleBuyResult("FAIL", null, message);
    }

    private FlashSaleVO toFlashSaleVO(FlashSale sale) {
        FlashSaleVO vo = new FlashSaleVO();
        vo.setId(sale.getId());
        vo.setTitle(sale.getTitle());
        vo.setStartTime(sale.getStartTime());
        vo.setEndTime(sale.getEndTime());
        vo.setStatus(sale.getStatus());
        return vo;
    }

    private String getOrderStatusMessage(Integer status) {
        if (status == null) {
            return "未知状态";
        }
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "已支付";
            case 2 -> "已发货";
            case 3 -> "已完成";
            case 4 -> "已取消";
            default -> "未知状态";
        };
    }

    @Override
    public FlashSaleBuyResult appeal(Long userId, Long flashSaleId) {
        FlashSale flashSale = flashSaleMapper.selectById(flashSaleId);
        if (flashSale == null) {
            return fail("活动不存在");
        }

        // 查找该用户在此活动下的失败订单
        List<FlashSaleFailedOrder> failedOrders = failedOrderMapper.selectList(
                new LambdaQueryWrapper<FlashSaleFailedOrder>()
                        .eq(FlashSaleFailedOrder::getUserId, userId)
                        .eq(FlashSaleFailedOrder::getFlashSaleId, flashSaleId)
                        .eq(FlashSaleFailedOrder::getStatus, 0));

        if (failedOrders.isEmpty()) {
            return fail("暂无失败订单，无需申诉");
        }

        // 标记为申诉中（status=2），等待人工或自动处理
        for (FlashSaleFailedOrder fo : failedOrders) {
            fo.setStatus(2);
            failedOrderMapper.updateById(fo);
        }

        saveAuditLog(flashSaleId, userId, "APPEAL", "用户 " + userId + " 申诉 " + failedOrders.size() + " 笔失败订单");
        log.info("秒杀申诉提交: userId={}, flashSaleId={}, 失败订单数={}", userId, flashSaleId, failedOrders.size());
        return new FlashSaleBuyResult("SUCCESS", null, "申诉已提交，共 " + failedOrders.size() + " 笔订单");
    }

    @Override
    public Map<String, Object> getFailedOrders(int page, int size) {
        Page<FlashSaleFailedOrder> pageParam = new Page<>(page, size);
        Page<FlashSaleFailedOrder> result = failedOrderMapper.selectPage(pageParam,
                new LambdaQueryWrapper<FlashSaleFailedOrder>()
                        .orderByDesc(FlashSaleFailedOrder::getCreateTime));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total", result.getTotal());
        map.put("records", result.getRecords());
        return map;
    }

    @Override
    public void retryFailedOrder(Long failedOrderId, Long operatorId) {
        FlashSaleFailedOrder failedOrder = failedOrderMapper.selectById(failedOrderId);
        if (failedOrder == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "失败订单不存在");
        }
        if (failedOrder.getStatus() != 0) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "该订单不在待重试状态");
        }

        // 重新发送 MQ 消息
        Map<String, Object> payload = Map.of(
                "transactionId", failedOrder.getTransactionId(),
                "flashSaleId", failedOrder.getFlashSaleId(),
                "productId", failedOrder.getProductId(),
                "userId", failedOrder.getUserId());
        mqProducer.send(MqConstants.TOPIC_FLASH_ORDER, MqConstants.TAG_FLASH_ORDER,
                failedOrder.getTransactionId(), payload);

        failedOrder.setStatus(1);
        failedOrder.setRetryCount(failedOrder.getRetryCount() + 1);
        failedOrderMapper.updateById(failedOrder);

        saveAuditLog(failedOrder.getFlashSaleId(), operatorId, "RETRY",
                "重试失败订单 " + failedOrderId);
        log.info("秒杀失败订单重试: failedOrderId={}, 操作人={}", failedOrderId, operatorId);
    }

    @Override
    public void cancelFailedOrder(Long failedOrderId, Long operatorId) {
        FlashSaleFailedOrder failedOrder = failedOrderMapper.selectById(failedOrderId);
        if (failedOrder == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "失败订单不存在");
        }

        // 回补库存
        String stockKey = FLASH_STOCK_PREFIX + failedOrder.getProductId() + "}:stock";
        Long currentStock = stringRedisTemplate.opsForValue().increment(stockKey);
        if (currentStock != null) {
            cacheManager.syncLocalStock(failedOrder.getProductId(), currentStock.intValue());
            cacheManager.broadcastStockChange(failedOrder.getProductId(), currentStock.intValue());
        }

        failedOrder.setStatus(3);
        failedOrderMapper.updateById(failedOrder);

        saveAuditLog(failedOrder.getFlashSaleId(), operatorId, "CANCEL_FAILED",
                "取消失败订单 " + failedOrderId + "，已回补库存");
        log.info("秒杀失败订单取消并回补: failedOrderId={}, 操作人={}", failedOrderId, operatorId);
    }

    @Override
    public void updateRateConfig(Long frequentThreshold, Long maliciousThreshold, Long blacklistMinutes, Long operatorId) {
        rateLimiter.updateConfig(frequentThreshold, maliciousThreshold, blacklistMinutes);
        saveAuditLog(null, operatorId, "RATE_CONFIG",
                "更新限流配置: frequent=" + frequentThreshold + ", malicious=" + maliciousThreshold + ", blacklistMinutes=" + blacklistMinutes);
    }

    @Override
    public Map<String, Object> getRateConfig() {
        return rateLimiter.getConfig();
    }

    @Override
    public void compensateFailedOrder(Long failedOrderId, BigDecimal amount, String remark, Long operatorId) {
        FlashSaleFailedOrder failed = failedOrderMapper.selectById(failedOrderId);
        if (failed == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "失败订单不存在");
        }
        if (failed.getStatus() != 0) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "该订单已处理（status=" + failed.getStatus() + "）");
        }

        FlashSaleCompensation compensation = new FlashSaleCompensation();
        compensation.setFailedOrderId(failedOrderId);
        compensation.setUserId(failed.getUserId());
        compensation.setFlashSaleId(failed.getFlashSaleId());
        compensation.setCompensationType("COUPON");
        compensation.setAmount(amount != null ? amount : BigDecimal.ZERO);
        compensation.setStatus(1);
        compensation.setOperatorId(operatorId);
        compensation.setRemark(remark);
        compensation.setCreateTime(LocalDateTime.now());
        compensationMapper.insert(compensation);

        // 标记失败订单为已补偿
        failed.setStatus(1);
        failedOrderMapper.updateById(failed);

        saveAuditLog(failed.getFlashSaleId(), operatorId, "COMPENSATE",
                "补偿失败订单 " + failedOrderId + "，金额=" + compensation.getAmount() + "，备注=" + remark);
        log.info("秒杀失败订单补偿: failedOrderId={}, amount={}, 操作人={}", failedOrderId, amount, operatorId);
    }

    @Override
    public void manualProcessFailedOrder(Long failedOrderId, String remark, Long operatorId) {
        FlashSaleFailedOrder failed = failedOrderMapper.selectById(failedOrderId);
        if (failed == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "失败订单不存在");
        }
        if (failed.getStatus() != 0 && failed.getStatus() != 1) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "该订单已处理（status=" + failed.getStatus() + "）");
        }

        FlashSaleCompensation compensation = new FlashSaleCompensation();
        compensation.setFailedOrderId(failedOrderId);
        compensation.setUserId(failed.getUserId());
        compensation.setFlashSaleId(failed.getFlashSaleId());
        compensation.setCompensationType("MANUAL");
        compensation.setAmount(BigDecimal.ZERO);
        compensation.setStatus(1);
        compensation.setOperatorId(operatorId);
        compensation.setRemark(remark);
        compensation.setCreateTime(LocalDateTime.now());
        compensationMapper.insert(compensation);

        failed.setStatus(2);
        failedOrderMapper.updateById(failed);

        saveAuditLog(failed.getFlashSaleId(), operatorId, "MANUAL_PROCESS",
                "人工处理失败订单 " + failedOrderId + "，备注=" + remark);
        log.info("秒杀失败订单人工处理: failedOrderId={}, 操作人={}", failedOrderId, operatorId);
    }
}
