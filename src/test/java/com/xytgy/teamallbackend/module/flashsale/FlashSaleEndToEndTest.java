package com.xytgy.teamallbackend.module.flashsale;

import com.xytgy.teamallbackend.module.flashsale.dto.CaptchaVerifyRequest;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleBuyRequest;
import com.xytgy.teamallbackend.module.flashsale.service.FlashSaleCoreServicePractice;
import com.xytgy.teamallbackend.module.flashsale.service.FlashSaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端测试
 * 测试完整的用户流程
 */
@SpringBootTest
public class FlashSaleEndToEndTest {

    @Autowired
    private FlashSaleCoreServicePractice flashSaleCoreServicePractice;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private DataSource dataSource;

    private static final Long TEST_USER_ID = 17L;
    private static final Long TEST_FLASH_SALE_ID = 1L;
    private static final Long TEST_PRODUCT_ID = 9L;

    /**
     * 测试完整用户流程
     * 用户从获取验证码到购买成功的完整流程
     */
    @Test
    public void testFullUserFlow_E2E() {
        System.out.println("=== 端到端测试：完整用户流程 ===");
        
        // 步骤1：获取验证码
        System.out.println("步骤1：获取验证码");
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        assertNotNull(captchaResult.uuid(), "UUID不能为空");
        assertNotNull(captchaResult.imageBase64(), "图片不能为空");
        System.out.println("  UUID: " + captchaResult.uuid());
        
        // 步骤2：从Redis获取验证码
        System.out.println("步骤2：获取验证码值");
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        assertNotNull(code, "验证码不能为空");
        System.out.println("  验证码: " + code);
        
        // 步骤3：验证验证码
        System.out.println("步骤3：验证验证码");
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(captchaResult.uuid());
        verifyRequest.setCode(code);
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
        assertNotNull(captchaToken, "captchaToken不能为空");
        assertFalse(captchaToken.equals("验证失败"), "验证码验证应该成功");
        System.out.println("  captchaToken: " + captchaToken);
        
        // 步骤4：购买商品
        System.out.println("步骤4：购买商品");
        FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
        buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
        buyRequest.setProductId(TEST_PRODUCT_ID);
        buyRequest.setCaptchaToken(captchaToken);
        buyRequest.setDeviceFingerprint("e2e-test-fingerprint");
        FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
        System.out.println("  购买结果: " + buyResult);
        
        // 步骤5：验证结果
        System.out.println("步骤5：验证结果");
        assertEquals("SUCCESS", buyResult.status(), "购买应该成功");
        assertNotNull(buyResult.orderId(), "订单ID不能为空");
        
        // 步骤6：验证数据库
        System.out.println("步骤6：验证数据库");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        
        // 验证库存减少
        Integer stockAfter = jdbcTemplate.queryForObject(
            "SELECT total_stock - sold_count FROM flash_sale_product WHERE flash_sale_id = ? AND product_id = ?",
            Integer.class, TEST_FLASH_SALE_ID, TEST_PRODUCT_ID);
        assertNotNull(stockAfter, "库存不能为空");
        System.out.println("  剩余库存: " + stockAfter);
        
        // 验证订单创建
        Integer orderCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE id = ?",
            Integer.class, buyResult.orderId());
        assertEquals(1, orderCount, "订单应该存在");
        System.out.println("  订单存在: true");
        
        System.out.println("=== 端到端测试通过 ===");
    }

    /**
     * 测试多用户同时抢购
     */
    @Test
    public void testMultipleUsers_E2E() throws InterruptedException {
        System.out.println("=== 端到端测试：多用户同时抢购 ===");
        
        // 步骤1：设置库存为3
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("UPDATE flash_sale_product SET sold_count = total_stock - 3 WHERE flash_sale_id = " + TEST_FLASH_SALE_ID + " AND product_id = " + TEST_PRODUCT_ID);
        System.out.println("已设置库存为3");
        
        int threadCount = 10;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failCount = new java.util.concurrent.atomic.AtomicInteger(0);
        
        try {
            // 步骤2：启动多个线程同时购买
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                new Thread(() -> {
                    try {
                        // 每个线程使用不同的用户ID
                        Long userId = 17L + threadIndex;
                        
                        // 获取captchaToken
                        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(userId);
                        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
                        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
                        verifyRequest.setUuid(captchaResult.uuid());
                        verifyRequest.setCode(code);
                        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(userId, verifyRequest);
                        
                        // 购买
                        FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
                        buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
                        buyRequest.setProductId(TEST_PRODUCT_ID);
                        buyRequest.setCaptchaToken(captchaToken);
                        FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(userId, buyRequest);
                        
                        if (buyResult.status().equals("SUCCESS")) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }
            
            // 步骤3：等待所有线程完成
            latch.await();
            
            // 步骤4：验证结果
            System.out.println("成功次数: " + successCount.get());
            System.out.println("失败次数: " + failCount.get());
            
            // 验证：成功次数应该等于库存数
            assertEquals(3, successCount.get(), "成功次数应该等于库存数");
            assertEquals(threadCount - 3, failCount.get(), "其他应该失败");
            
            // 验证：库存应该为0
            Integer stockAfter = jdbcTemplate.queryForObject(
                "SELECT total_stock - sold_count FROM flash_sale_product WHERE flash_sale_id = ? AND product_id = ?",
                Integer.class, TEST_FLASH_SALE_ID, TEST_PRODUCT_ID);
            assertEquals(0, stockAfter, "库存应该为0");
            
            System.out.println("=== 多用户同时抢购测试通过 ===");
        } finally {
            // 恢复库存
            jdbcTemplate.execute("UPDATE flash_sale_product SET sold_count = 0 WHERE flash_sale_id = " + TEST_FLASH_SALE_ID + " AND product_id = " + TEST_PRODUCT_ID);
            System.out.println("已恢复库存");
        }
    }

    /**
     * 测试验证码输错后重新获取
     */
    @Test
    public void testUserFlowWithCaptchaError_E2E() {
        System.out.println("=== 端到端测试：验证码输错后重新获取 ===");
        
        // 步骤1：获取验证码
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String uuid = captchaResult.uuid();
        
        // 步骤2：输入错误验证码
        CaptchaVerifyRequest wrongRequest = new CaptchaVerifyRequest();
        wrongRequest.setUuid(uuid);
        wrongRequest.setCode("0000");
        String wrongResult = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, wrongRequest);
        assertEquals("验证失败", wrongResult, "应该返回验证失败");
        
        // 步骤3：重新获取验证码
        FlashSaleService.CaptchaResult newCaptchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String newUuid = newCaptchaResult.uuid();
        String newCode = stringRedisTemplate.opsForValue().get("captcha:" + newUuid);
        
        // 步骤4：输入正确验证码
        CaptchaVerifyRequest correctRequest = new CaptchaVerifyRequest();
        correctRequest.setUuid(newUuid);
        correctRequest.setCode(newCode);
        String correctResult = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, correctRequest);
        assertNotNull(correctResult, "captchaToken不能为空");
        assertFalse(correctResult.equals("验证失败"), "验证码验证应该成功");
        
        System.out.println("=== 验证码输错后重新获取测试通过 ===");
    }

    /**
     * 测试库存不足时的用户流程
     */
    @Test
    public void testUserFlowWithStockInsufficient_E2E() {
        System.out.println("=== 端到端测试：库存不足 ===");
        
        // 步骤1：设置库存为0
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("UPDATE flash_sale_product SET sold_count = total_stock WHERE flash_sale_id = " + TEST_FLASH_SALE_ID + " AND product_id = " + TEST_PRODUCT_ID);
        
        try {
            // 步骤2：获取captchaToken
            FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
            String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
            CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
            verifyRequest.setUuid(captchaResult.uuid());
            verifyRequest.setCode(code);
            String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
            
            // 步骤3：尝试购买
            FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
            buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
            buyRequest.setProductId(TEST_PRODUCT_ID);
            buyRequest.setCaptchaToken(captchaToken);
            FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
            
            // 步骤4：验证结果
            assertEquals("FAIL", buyResult.status(), "应该返回FAIL");
            assertEquals("库存不足", buyResult.message(), "应该返回库存不足");
            
            System.out.println("=== 库存不足测试通过 ===");
        } finally {
            // 恢复库存
            jdbcTemplate.execute("UPDATE flash_sale_product SET sold_count = 0 WHERE flash_sale_id = " + TEST_FLASH_SALE_ID + " AND product_id = " + TEST_PRODUCT_ID);
        }
    }
}
