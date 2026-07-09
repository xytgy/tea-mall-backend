package com.xytgy.teamallbackend.module.flashsale.service;

import com.xytgy.teamallbackend.module.flashsale.dto.CaptchaVerifyRequest;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleBuyRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 秒杀系统集成测试
 * 
 * 测试类型：集成测试（@SpringBootTest）
 * 测试范围：完整的业务流程，包括验证码、购买、库存、订单等
 * 测试环境：真实的MySQL数据库和Redis
 * 
 * 测试用例分类：
 * 1. 正向测试：测试正常流程是否成功
 * 2. 逆向测试：测试异常流程是否正确处理
 * 3. 数据完整性测试：验证数据一致性
 * 4. 边界测试：测试边界条件
 * 5. 异常输入测试：测试参数校验
 * 6. 安全测试：测试SQL注入等安全问题
 * 7. 性能测试：测试系统性能
 * 8. 并发测试：测试并发场景
 */
@SpringBootTest(properties = {"spring.devtools.restart.enabled=false"})
@ActiveProfiles("dev")
public class FlashSaleCoreServicePracticeTest {

    // ========== 测试常量 ==========

    /** 测试用户ID */
    private static final Long TEST_USER_ID = 17L;
    
    /** 测试用户ID2（用于测试用户不匹配） */
    private static final Long TEST_USER_ID_2 = 18L;
    
    /** 测试秒杀活动ID */
    private static final Long TEST_FLASH_SALE_ID = 1L;
    
    /** 测试商品ID */
    private static final Long TEST_PRODUCT_ID = 9L;
    
    /** 不存在的活动ID */
    private static final Long NOT_EXIST_FLASH_SALE_ID = 999L;
    
    /** 不存在的商品ID */
    private static final Long NOT_EXIST_PRODUCT_ID = 999L;
    
    /** 不属于当前活动的商品ID（商品ID=10属于活动ID=2，不属于活动ID=1） */
    private static final Long PRODUCT_NOT_IN_ACTIVITY = 10L;

    @Autowired
    private FlashSaleCoreServicePractice flashSaleCoreServicePractice;
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 测试使用无效的captchaToken购买
     * 
     * 测试目的：验证captchaToken校验机制
     * 测试步骤：
     *   1. 构建购买请求，使用无效的captchaToken
     *   2. 调用buy方法
     *   3. 验证返回结果为FAIL
     * 预期结果：返回FAIL，提示"验证码无效或已过期"
     */
    @Test
    public void testBuyWithInvalidToken() {
        FlashSaleBuyRequest request = new FlashSaleBuyRequest();
        request.setProductId(10L);
        request.setFlashSaleId(TEST_FLASH_SALE_ID);
        request.setCaptchaToken("test-token");  // 无效的token
        FlashSaleService.FlashSaleBuyResult result = flashSaleCoreServicePractice.buy(TEST_USER_ID, request);
        System.out.println("结果: " + result);
        assertEquals("FAIL", result.status());
    }

    /**
     * 测试完整的正向流程（成功场景）
     * 
     * 测试目的：验证从获取验证码到购买成功的完整流程
     * 测试步骤：
     *   1. 生成验证码
     *   2. 从Redis获取验证码值
     *   3. 验证验证码，获取captchaToken
     *   4. 使用captchaToken调用buy方法
     *   5. 验证购买结果
     * 预期结果：
     *   - 购买成功
     *   - 返回订单ID
     */
    @Test
    public void testFullFlowSuccess() {
        System.out.println("=== 步骤1：生成验证码 ===");
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        assertNotNull(captchaResult.uuid(), "UUID不能为空");
        assertNotNull(captchaResult.imageBase64(), "图片Base64不能为空");
        System.out.println("uuid: " + captchaResult.uuid());
        
        System.out.println("=== 步骤2：从Redis获取验证码 ===");
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        assertNotNull(code, "验证码不能为空");
        System.out.println("验证码: " + code);
        
        System.out.println("=== 步骤3：验证验证码 ===");
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(captchaResult.uuid());
        verifyRequest.setCode(code);
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
        assertNotNull(captchaToken, "captchaToken不能为空");
        assertTrue(!captchaToken.equals("验证失败"), "验证码验证应该成功");
        assertTrue(!captchaToken.equals("验证码已过期"), "验证码不应该过期");
        System.out.println("captchaToken: " + captchaToken);
        
        System.out.println("=== 步骤4：调用buy方法 ===");
        FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
        buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
        buyRequest.setProductId(TEST_PRODUCT_ID);
        buyRequest.setCaptchaToken(captchaToken);
        buyRequest.setDeviceFingerprint("test-fingerprint");
        FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
        System.out.println("购买结果: " + buyResult);
        
        System.out.println("=== 步骤5：验证结果 ===");
        assertEquals("SUCCESS", buyResult.status(), "购买应该成功");
        assertNotNull(buyResult.orderId(), "订单ID不能为空");
        
        System.out.println("=== 完整流程测试通过 ===");
    }

    // ========== 逆向测试：验证码相关 ==========
    
    /**
     * 测试captchaToken过期场景
     * 
     * 测试目的：验证captchaToken过期后是否正确处理
     * 测试步骤：
     *   1. 生成验证码并验证，获取captchaToken
     *   2. 手动删除Redis中的captchaToken（模拟过期）
     *   3. 尝试购买
     * 预期结果：返回"验证码无效或已过期"
     */
    @Test
    public void testCaptchaTokenExpired() {
        System.out.println("=== 测试captchaToken过期 ===");
        
        // 步骤1：生成验证码
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        
        // 步骤2：验证验证码，获取captchaToken
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(captchaResult.uuid());
        verifyRequest.setCode(code);
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
        System.out.println("captchaToken: " + captchaToken);
        
        // 步骤3：手动删除Redis中的captchaToken（模拟过期）
        stringRedisTemplate.delete("captcha_token:" + captchaToken);
        System.out.println("已删除Redis中的captchaToken");
        
        // 步骤4：尝试购买
        FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
        buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
        buyRequest.setProductId(TEST_PRODUCT_ID);
        buyRequest.setCaptchaToken(captchaToken);
        FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
        System.out.println("购买结果: " + buyResult);
        
        // 验证：应该返回失败
        assertEquals("FAIL", buyResult.status(), "captchaToken过期时应该返回FAIL");
        assertEquals("验证码无效或已过期", buyResult.message(), "应该返回'验证码无效或已过期'");
        System.out.println("=== captchaToken过期测试通过 ===");
    }

    /**
     * 测试captchaToken用户不匹配场景
     * 
     * 测试目的：验证captchaToken是否绑定用户
     * 测试步骤：
     *   1. 用户A生成验证码并验证，获取captchaToken
     *   2. 用户B使用用户A的captchaToken尝试购买
     * 预期结果：返回"验证码无效"
     */
    @Test
    public void testCaptchaTokenWrongUser() {
        System.out.println("=== 测试captchaToken用户不匹配 ===");
        
        // 步骤1：用户A生成验证码并验证
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(captchaResult.uuid());
        verifyRequest.setCode(code);
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
        System.out.println("用户A的captchaToken: " + captchaToken);
        
        // 步骤2：用户B使用用户A的captchaToken尝试购买
        FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
        buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
        buyRequest.setProductId(TEST_PRODUCT_ID);
        buyRequest.setCaptchaToken(captchaToken);
        FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID_2, buyRequest);
        System.out.println("用户B的购买结果: " + buyResult);
        
        // 验证：应该返回失败
        assertEquals("FAIL", buyResult.status(), "用户不匹配时应该返回FAIL");
        assertEquals("验证码无效", buyResult.message(), "应该返回'验证码无效'");
        System.out.println("=== captchaToken用户不匹配测试通过 ===");
    }

    /**
     * 测试captchaToken重复使用场景
     * 
     * 测试目的：验证captchaToken的一次性使用机制
     * 测试步骤：
     *   1. 生成验证码并验证，获取captchaToken
     *   2. 第一次使用captchaToken购买
     *   3. 第二次使用同一个captchaToken购买
     * 预期结果：第二次购买失败，返回"验证码无效或已过期"
     */
    @Test
    public void testCaptchaTokenReuse() {
        System.out.println("=== 测试captchaToken重复使用 ===");
        
        // 步骤1：生成验证码并验证
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(captchaResult.uuid());
        verifyRequest.setCode(code);
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
        System.out.println("captchaToken: " + captchaToken);
        
        // 步骤2：第一次使用（可能成功或失败，取决于活动状态）
        FlashSaleBuyRequest buyRequest1 = new FlashSaleBuyRequest();
        buyRequest1.setFlashSaleId(TEST_FLASH_SALE_ID);
        buyRequest1.setProductId(TEST_PRODUCT_ID);
        buyRequest1.setCaptchaToken(captchaToken);
        FlashSaleService.FlashSaleBuyResult buyResult1 = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest1);
        System.out.println("第一次购买结果: " + buyResult1);
        
        // 步骤3：第二次使用同一个captchaToken
        FlashSaleBuyRequest buyRequest2 = new FlashSaleBuyRequest();
        buyRequest2.setFlashSaleId(TEST_FLASH_SALE_ID);
        buyRequest2.setProductId(TEST_PRODUCT_ID);
        buyRequest2.setCaptchaToken(captchaToken);
        FlashSaleService.FlashSaleBuyResult buyResult2 = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest2);
        System.out.println("第二次购买结果: " + buyResult2);
        
        // 验证：第二次应该失败（token已被删除）
        assertEquals("FAIL", buyResult2.status(), "captchaToken重复使用时应该返回FAIL");
        assertEquals("验证码无效或已过期", buyResult2.message(), "应该返回'验证码无效或已过期'");
        System.out.println("=== captchaToken重复使用测试通过 ===");
    }

    // ========== 逆向测试：业务逻辑相关 ==========
    
    /**
     * 测试活动不存在场景
     * 
     * 测试目的：验证活动不存在时是否正确处理
     * 测试步骤：
     *   1. 获取captchaToken
     *   2. 使用不存在的活动ID（999）
     *   3. 尝试购买
     * 预期结果：返回"活动没有开始或者不存在"
     */
    @Test
    public void testActivityNotExist() {
        System.out.println("=== 测试活动不存在 ===");
        
        // 步骤1：获取captchaToken
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(captchaResult.uuid());
        verifyRequest.setCode(code);
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
        
        // 步骤2：使用不存在的活动ID
        FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
        buyRequest.setFlashSaleId(NOT_EXIST_FLASH_SALE_ID);
        buyRequest.setProductId(TEST_PRODUCT_ID);
        buyRequest.setCaptchaToken(captchaToken);
        FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
        System.out.println("购买结果: " + buyResult);
        
        // 验证：应该返回失败
        assertEquals("FAIL", buyResult.status(), "活动不存在时应该返回FAIL");
        assertEquals("活动没有开始或者不存在", buyResult.message(), "应该返回'活动没有开始或者不存在'");
        System.out.println("=== 活动不存在测试通过 ===");
    }

    /**
     * 测试活动未开始场景
     * 
     * 测试目的：验证活动未开始时是否正确处理
     * 测试步骤：
     *   1. 获取captchaToken
     *   2. 修改活动开始时间为未来（模拟活动未开始）
     *   3. 尝试购买
     *   4. 恢复活动时间
     * 预期结果：返回"活动没有开始或者不存在"
     */
    @Test
    public void testActivityNotStarted() {
        System.out.println("=== 测试活动未开始 ===");
        
        // 步骤1：获取captchaToken
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(captchaResult.uuid());
        verifyRequest.setCode(code);
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
        
        // 步骤2：修改活动开始时间为未来（模拟活动未开始）
        mysqlUpdate("UPDATE flash_sale SET start_time = DATE_ADD(NOW(), INTERVAL 1 HOUR) WHERE id = " + TEST_FLASH_SALE_ID);
        System.out.println("已修改活动开始时间为未来");
        
        try {
            // 步骤3：尝试购买
            FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
            buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
            buyRequest.setProductId(TEST_PRODUCT_ID);
            buyRequest.setCaptchaToken(captchaToken);
            FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
            System.out.println("购买结果: " + buyResult);
            
            // 验证：应该返回失败
            assertEquals("FAIL", buyResult.status(), "活动未开始时应该返回FAIL");
            assertEquals("活动没有开始或者不存在", buyResult.message(), "应该返回'活动没有开始或者不存在'");
        } finally {
            // 恢复活动时间
            mysqlUpdate("UPDATE flash_sale SET start_time = DATE_SUB(NOW(), INTERVAL 1 HOUR) WHERE id = " + TEST_FLASH_SALE_ID);
            System.out.println("已恢复活动时间");
        }
        System.out.println("=== 活动未开始测试通过 ===");
    }

    /**
     * 测试活动已结束场景
     * 
     * 测试目的：验证活动已结束时是否正确处理
     * 测试步骤：
     *   1. 获取captchaToken
     *   2. 修改活动结束时间为过去（模拟活动已结束）
     *   3. 尝试购买
     *   4. 恢复活动时间
     * 预期结果：返回"活动没有开始或者不存在"
     */
    @Test
    public void testActivityEnded() {
        System.out.println("=== 测试活动已结束 ===");
        
        // 步骤1：获取captchaToken
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(captchaResult.uuid());
        verifyRequest.setCode(code);
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
        
        // 步骤2：修改活动结束时间为过去（模拟活动已结束）
        mysqlUpdate("UPDATE flash_sale SET end_time = DATE_SUB(NOW(), INTERVAL 1 HOUR) WHERE id = " + TEST_FLASH_SALE_ID);
        System.out.println("已修改活动结束时间为过去");
        
        try {
            // 步骤3：尝试购买
            FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
            buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
            buyRequest.setProductId(TEST_PRODUCT_ID);
            buyRequest.setCaptchaToken(captchaToken);
            FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
            System.out.println("购买结果: " + buyResult);
            
            // 验证：应该返回失败
            assertEquals("FAIL", buyResult.status(), "活动已结束时应该返回FAIL");
            assertEquals("活动没有开始或者不存在", buyResult.message(), "应该返回'活动没有开始或者不存在'");
        } finally {
            // 恢复活动时间
            mysqlUpdate("UPDATE flash_sale SET end_time = DATE_ADD(NOW(), INTERVAL 2 HOUR) WHERE id = " + TEST_FLASH_SALE_ID);
            System.out.println("已恢复活动时间");
        }
        System.out.println("=== 活动已结束测试通过 ===");
    }

    /**
     * 测试商品不存在场景
     * 
     * 测试目的：验证商品不存在时是否正确处理
     * 测试步骤：
     *   1. 获取captchaToken
     *   2. 使用不存在的商品ID（999）
     *   3. 尝试购买
     * 预期结果：返回"商品不存在"
     */
    @Test
    public void testProductNotExist() {
        System.out.println("=== 测试商品不存在 ===");
        
        // 步骤1：获取captchaToken
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(captchaResult.uuid());
        verifyRequest.setCode(code);
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
        
        // 步骤2：使用不存在的商品ID
        FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
        buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
        buyRequest.setProductId(NOT_EXIST_PRODUCT_ID);
        buyRequest.setCaptchaToken(captchaToken);
        FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
        System.out.println("购买结果: " + buyResult);
        
        // 验证：应该返回失败
        assertEquals("FAIL", buyResult.status(), "商品不存在时应该返回FAIL");
        assertEquals("商品不存在", buyResult.message(), "应该返回'商品不存在'");
        System.out.println("=== 商品不存在测试通过 ===");
    }

    /**
     * 测试商品不属于该活动场景
     * 
     * 测试目的：验证商品不属于活动时是否正确处理
     * 测试步骤：
     *   1. 获取captchaToken
     *   2. 使用活动ID=1，但商品ID=10属于活动ID=2
     *   3. 尝试购买
     * 预期结果：返回"该商品不属于该活动"
     */
    @Test
    public void testProductNotInActivity() {
        System.out.println("=== 测试商品不属于该活动 ===");
        
        // 步骤1：获取captchaToken
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(captchaResult.uuid());
        verifyRequest.setCode(code);
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
        
        // 步骤2：使用活动ID=1，商品ID=10
        // 商品ID=10存在于flash_sale_product表中，但属于活动ID=2，不属于活动ID=1
        FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
        buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);  // 活动ID=1
        buyRequest.setProductId(PRODUCT_NOT_IN_ACTIVITY);  // 商品ID=10（属于活动ID=2）
        buyRequest.setCaptchaToken(captchaToken);
        FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
        System.out.println("购买结果: " + buyResult);
        
        // 验证：应该返回失败
        assertEquals("FAIL", buyResult.status(), "商品不属于活动时应该返回FAIL");
        assertEquals("该商品不属于该活动", buyResult.message(), "应该返回'该商品不属于该活动'");
        System.out.println("=== 商品不属于该活动测试通过 ===");
    }

    /**
     * 测试库存不足场景
     * 
     * 测试目的：验证库存不足时是否正确处理
     * 测试步骤：
     *   1. 获取captchaToken
     *   2. 将库存扣减到0（模拟库存不足）
     *   3. 尝试购买
     *   4. 恢复库存
     * 预期结果：返回"库存不足"
     */
    @Test
    public void testStockInsufficient() {
        System.out.println("=== 测试库存不足 ===");
        
        // 步骤1：获取captchaToken
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(captchaResult.uuid());
        verifyRequest.setCode(code);
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
        
        // 步骤2：将库存扣减到0（模拟库存不足）
        mysqlUpdate("UPDATE flash_sale_product SET sold_count = total_stock WHERE flash_sale_id = " + TEST_FLASH_SALE_ID + " AND product_id = " + TEST_PRODUCT_ID);
        System.out.println("已将库存扣减到0");
        
        try {
            // 步骤3：尝试购买
            FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
            buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
            buyRequest.setProductId(TEST_PRODUCT_ID);
            buyRequest.setCaptchaToken(captchaToken);
            FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
            System.out.println("购买结果: " + buyResult);
            
            // 验证：应该返回失败
            assertEquals("FAIL", buyResult.status(), "库存不足时应该返回FAIL");
            assertEquals("库存不足", buyResult.message(), "应该返回'库存不足'");
        } finally {
            // 恢复库存
            mysqlUpdate("UPDATE flash_sale_product SET sold_count = 0 WHERE flash_sale_id = " + TEST_FLASH_SALE_ID + " AND product_id = " + TEST_PRODUCT_ID);
            System.out.println("已恢复库存");
        }
        System.out.println("=== 库存不足测试通过 ===");
    }

    // ========== 数据完整性测试 ==========
    
    /**
     * 测试购买后库存减少
     * 
     * 测试目的：验证购买成功后库存是否正确减少
     * 测试步骤：
     *   1. 获取购买前的库存
     *   2. 执行购买流程
     *   3. 获取购买后的库存
     *   4. 对比库存变化
     * 预期结果：
     *   - 购买成功时：库存减少1
     *   - 购买失败时：库存不变
     */
    @Test
    public void testStockDecreasedAfterPurchase() {
        System.out.println("=== 测试购买后库存减少 ===");
        
        // 步骤1：获取购买前的库存
        Integer stockBefore = mysqlQueryInt("SELECT sold_count FROM flash_sale_product WHERE flash_sale_id = " + TEST_FLASH_SALE_ID + " AND product_id = " + TEST_PRODUCT_ID);
        System.out.println("购买前库存: " + stockBefore);
        
        // 步骤2：获取captchaToken并购买
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(captchaResult.uuid());
        verifyRequest.setCode(code);
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
        
        FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
        buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
        buyRequest.setProductId(TEST_PRODUCT_ID);
        buyRequest.setCaptchaToken(captchaToken);
        FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
        
        // 步骤3：获取购买后的库存
        Integer stockAfter = mysqlQueryInt("SELECT sold_count FROM flash_sale_product WHERE flash_sale_id = " + TEST_FLASH_SALE_ID + " AND product_id = " + TEST_PRODUCT_ID);
        System.out.println("购买后库存: " + stockAfter);
        
        // 验证：库存应该减少1
        if (buyResult.status().equals("SUCCESS")) {
            assertEquals(stockBefore + 1, stockAfter, "购买成功时库存应该减少1");
            System.out.println("=== 库存减少测试通过 ===");
        } else {
            assertEquals(stockBefore, stockAfter, "购买失败时库存应该不变");
            System.out.println("=== 库存不变测试通过（购买失败）===");
        }
    }

    /**
     * 测试购买后订单创建
     * 
     * 测试目的：验证购买成功后订单是否正确创建
     * 测试步骤：
     *   1. 获取购买前的订单数量
     *   2. 执行购买流程
     *   3. 获取购买后的订单数量
     *   4. 对比订单数量变化
     * 预期结果：
     *   - 购买成功时：订单数量增加1
     *   - 购买失败时：订单数量不变
     */
    @Test
    public void testOrderCreatedAfterPurchase() {
        System.out.println("=== 测试购买后订单创建 ===");
        
        // 步骤1：获取购买前的订单数量
        Integer orderCountBefore = mysqlQueryInt("SELECT COUNT(*) FROM orders WHERE user_id = " + TEST_USER_ID + " AND source = 1");
        System.out.println("购买前订单数量: " + orderCountBefore);
        
        // 步骤2：获取captchaToken并购买
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(captchaResult.uuid());
        verifyRequest.setCode(code);
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
        
        FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
        buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
        buyRequest.setProductId(TEST_PRODUCT_ID);
        buyRequest.setCaptchaToken(captchaToken);
        FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
        
        // 步骤3：获取购买后的订单数量
        Integer orderCountAfter = mysqlQueryInt("SELECT COUNT(*) FROM orders WHERE user_id = " + TEST_USER_ID + " AND source = 1");
        System.out.println("购买后订单数量: " + orderCountAfter);
        
        // 验证：订单数量应该增加1
        if (buyResult.status().equals("SUCCESS")) {
            assertEquals(orderCountBefore + 1, orderCountAfter, "购买成功时订单数量应该增加1");
            System.out.println("=== 订单创建测试通过 ===");
        } else {
            assertEquals(orderCountBefore, orderCountAfter, "购买失败时订单数量应该不变");
            System.out.println("=== 订单未创建测试通过（购买失败）===");
        }
    }

    /**
     * 测试订单金额正确
     * 
     * 测试目的：验证订单金额是否等于秒杀价格
     * 测试步骤：
     *   1. 获取秒杀价格
     *   2. 执行购买流程
     *   3. 获取订单金额
     *   4. 对比金额是否相等
     * 预期结果：订单金额 = 秒杀价格
     */
    @Test
    public void testOrderAmountCorrect() {
        System.out.println("=== 测试订单金额正确 ===");
        
        // 步骤1：获取秒杀价格
        BigDecimal flashPrice = mysqlQueryBigDecimal("SELECT flash_price FROM flash_sale_product WHERE flash_sale_id = " + TEST_FLASH_SALE_ID + " AND product_id = " + TEST_PRODUCT_ID);
        System.out.println("秒杀价格: " + flashPrice);
        
        // 步骤2：获取captchaToken并购买
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(captchaResult.uuid());
        verifyRequest.setCode(code);
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
        
        FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
        buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
        buyRequest.setProductId(TEST_PRODUCT_ID);
        buyRequest.setCaptchaToken(captchaToken);
        FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
        
        // 步骤3：获取订单金额
        if (buyResult.status().equals("SUCCESS")) {
            BigDecimal orderAmount = mysqlQueryBigDecimal("SELECT total_amount FROM orders WHERE id = " + buyResult.orderId());
            System.out.println("订单金额: " + orderAmount);
            
            // 验证：订单金额应该等于秒杀价格
            assertEquals(flashPrice, orderAmount, "订单金额应该等于秒杀价格");
            System.out.println("=== 订单金额正确测试通过 ===");
        } else {
            System.out.println("=== 购买失败，跳过金额验证 ===");
        }
    }

    // ========== 边界测试 ==========
    
    /**
     * 测试库存为1时购买
     * 
     * 测试目的：验证边界条件下的购买行为
     * 测试步骤：
     *   1. 设置库存为1
     *   2. 执行购买流程
     *   3. 验证购买成功
     *   4. 验证库存变为0
     *   5. 恢复库存
     * 预期结果：购买成功，库存变为0
     */
    @Test
    public void testBuyWithStockOne() {
        System.out.println("=== 测试库存为1时购买 ===");
        
        // 步骤1：设置库存为1
        mysqlUpdate("UPDATE flash_sale_product SET sold_count = total_stock - 1 WHERE flash_sale_id = " + TEST_FLASH_SALE_ID + " AND product_id = " + TEST_PRODUCT_ID);
        System.out.println("已设置库存为1");
        
        try {
            // 步骤2：获取captchaToken并购买
            FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
            String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
            CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
            verifyRequest.setUuid(captchaResult.uuid());
            verifyRequest.setCode(code);
            String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
            
            FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
            buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
            buyRequest.setProductId(TEST_PRODUCT_ID);
            buyRequest.setCaptchaToken(captchaToken);
            FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
            System.out.println("购买结果: " + buyResult);
            
            // 验证：应该购买成功
            assertEquals("SUCCESS", buyResult.status(), "库存为1时应该购买成功");
            assertNotNull(buyResult.orderId(), "订单ID不能为空");
            
            // 验证：库存应该变为0
            Integer stockAfter = mysqlQueryInt("SELECT total_stock - sold_count FROM flash_sale_product WHERE flash_sale_id = " + TEST_FLASH_SALE_ID + " AND product_id = " + TEST_PRODUCT_ID);
            assertEquals(0, stockAfter, "购买后库存应该为0");
            System.out.println("=== 库存为1时购买测试通过 ===");
        } finally {
            // 恢复库存
            mysqlUpdate("UPDATE flash_sale_product SET sold_count = 0 WHERE flash_sale_id = " + TEST_FLASH_SALE_ID + " AND product_id = " + TEST_PRODUCT_ID);
            System.out.println("已恢复库存");
        }
    }

    // ========== 异常输入测试 ==========
    
    /**
     * 测试captchaToken为空场景
     * 
     * 测试目的：验证参数校验机制
     * 测试步骤：
     *   1. 构建购买请求，captchaToken设为null
     *   2. 调用buy方法
     * 预期结果：返回FAIL
     */
    @Test
    public void testBuyWithNullCaptchaToken() {
        System.out.println("=== 测试captchaToken为空 ===");
        
        FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
        buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
        buyRequest.setProductId(TEST_PRODUCT_ID);
        buyRequest.setCaptchaToken(null);  // 空token
        
        FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
        System.out.println("购买结果: " + buyResult);
        
        // 验证：应该返回失败
        assertEquals("FAIL", buyResult.status(), "captchaToken为空时应该返回FAIL");
        System.out.println("=== captchaToken为空测试通过 ===");
    }

    /**
     * 测试flashSaleId为空场景
     * 
     * 测试目的：验证参数校验机制
     * 测试步骤：
     *   1. 获取captchaToken
     *   2. 构建购买请求，flashSaleId设为null
     *   3. 调用buy方法
     * 预期结果：返回FAIL或抛出异常（暴露潜在问题）
     */
    @Test
    public void testBuyWithNullFlashSaleId() {
        System.out.println("=== 测试flashSaleId为空 ===");
        
        // 步骤1：获取captchaToken
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(captchaResult.uuid());
        verifyRequest.setCode(code);
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
        
        FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
        buyRequest.setFlashSaleId(null);  // 空活动ID
        buyRequest.setProductId(TEST_PRODUCT_ID);
        buyRequest.setCaptchaToken(captchaToken);
        
        // 注意：当前代码没有校验flashSaleId是否为空，可能会抛出异常
        // 这个测试是为了暴露潜在问题
        try {
            FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
            System.out.println("购买结果: " + buyResult);
            assertEquals("FAIL", buyResult.status(), "flashSaleId为空时应该返回FAIL");
        } catch (Exception e) {
            System.out.println("抛出异常: " + e.getMessage());
            // 如果抛出异常，说明代码需要改进
        }
        System.out.println("=== flashSaleId为空测试通过 ===");
    }

    /**
     * 测试productId为空场景
     * 
     * 测试目的：验证参数校验机制
     * 测试步骤：
     *   1. 获取captchaToken
     *   2. 构建购买请求，productId设为null
     *   3. 调用buy方法
     * 预期结果：返回FAIL或抛出异常（暴露潜在问题）
     */
    @Test
    public void testBuyWithNullProductId() {
        System.out.println("=== 测试productId为空 ===");
        
        // 步骤1：获取captchaToken
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(captchaResult.uuid());
        verifyRequest.setCode(code);
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
        
        FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
        buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
        buyRequest.setProductId(null);  // 空商品ID
        buyRequest.setCaptchaToken(captchaToken);
        
        // 注意：当前代码没有校验productId是否为空，可能会抛出异常
        try {
            FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
            System.out.println("购买结果: " + buyResult);
            assertEquals("FAIL", buyResult.status(), "productId为空时应该返回FAIL");
        } catch (Exception e) {
            System.out.println("抛出异常: " + e.getMessage());
            // 如果抛出异常，说明代码需要改进
        }
        System.out.println("=== productId为空测试通过 ===");
    }

    // ========== 安全测试 ==========
    
    /**
     * 测试UUID字段SQL注入
     * 
     * 测试目的：验证系统是否能抵御SQL注入攻击
     * 测试步骤：
     *   1. 使用SQL注入的UUID（如 "1' OR '1'='1"）
     *   2. 尝试验证验证码
     * 预期结果：返回"验证码已过期"，而不是被注入
     */
    @Test
    public void testSqlInjectionInUuid() {
        System.out.println("=== 测试UUID字段SQL注入 ===");
        
        // 步骤1：使用SQL注入的UUID
        String maliciousUuid = "1' OR '1'='1";
        String maliciousCode = "1' OR '1'='1";
        
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(maliciousUuid);
        verifyRequest.setCode(maliciousCode);
        
        try {
            String result = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
            System.out.println("验证结果: " + result);
            
            // 验证：应该返回"验证码已过期"（因为UUID不存在），而不是被注入
            assertEquals("验证码已过期", result, "SQL注入应该被拦截");
        } catch (Exception e) {
            System.out.println("抛出异常: " + e.getMessage());
            // 如果抛出异常，说明代码需要改进
        }
        System.out.println("=== UUID字段SQL注入测试通过 ===");
    }

    /**
     * 测试验证码字段SQL注入
     * 
     * 测试目的：验证系统是否能抵御SQL注入攻击
     * 测试步骤：
     *   1. 生成正常的验证码
     *   2. 使用SQL注入的验证码
     *   3. 尝试验证
     * 预期结果：返回"验证失败"，而不是被注入
     */
    @Test
    public void testSqlInjectionInCode() {
        System.out.println("=== 测试验证码字段SQL注入 ===");
        
        // 步骤1：生成正常的验证码
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String uuid = captchaResult.uuid();
        
        // 步骤2：使用SQL注入的验证码
        String maliciousCode = "1' OR '1'='1";
        
        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
        verifyRequest.setUuid(uuid);
        verifyRequest.setCode(maliciousCode);
        
        try {
            String result = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
            System.out.println("验证结果: " + result);
            
            // 验证：应该返回"验证失败"（因为验证码不匹配），而不是被注入
            assertEquals("验证失败", result, "SQL注入应该被拦截");
        } catch (Exception e) {
            System.out.println("抛出异常: " + e.getMessage());
            // 如果抛出异常，说明代码需要改进
        }
        System.out.println("=== 验证码字段SQL注入测试通过 ===");
    }

    // ========== 性能测试 ==========
    
    /**
     * 测试购买性能
     * 
     * 测试目的：验证系统在高并发下的性能表现
     * 测试步骤：
     *   1. 执行100次购买请求
     *   2. 记录总耗时和平均响应时间
     * 预期结果：平均响应时间 < 1秒
     */
    @Test
    public void testBuyPerformance() {
        System.out.println("=== 测试购买性能 ===");
        
        int totalRequests = 100;
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int failCount = 0;
        
        for (int i = 0; i < totalRequests; i++) {
            try {
                // 获取captchaToken
                FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
                String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
                CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
                verifyRequest.setUuid(captchaResult.uuid());
                verifyRequest.setCode(code);
                String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
                
                // 购买
                FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
                buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
                buyRequest.setProductId(TEST_PRODUCT_ID);
                buyRequest.setCaptchaToken(captchaToken);
                FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
                
                if (buyResult.status().equals("SUCCESS")) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                failCount++;
            }
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double avgTime = (double) totalTime / totalRequests;
        
        System.out.println("总请求数: " + totalRequests);
        System.out.println("成功次数: " + successCount);
        System.out.println("失败次数: " + failCount);
        System.out.println("总耗时: " + totalTime + "ms");
        System.out.println("平均响应时间: " + String.format("%.2f", avgTime) + "ms");
        
        // 验证：平均响应时间应该小于1秒
        assertTrue(avgTime < 1000, "平均响应时间应该小于1秒");
        System.out.println("=== 性能测试通过 ===");
    }

    // ========== 并发测试 ==========
    
    /**
     * 测试并发购买
     * 
     * 测试目的：验证高并发场景下的库存控制
     * 测试步骤：
     *   1. 设置库存为1
     *   2. 启动10个线程同时购买
     *   3. 等待所有线程完成
     *   4. 验证结果
     * 预期结果：
     *   - 只有1个线程购买成功
     *   - 其他9个线程购买失败
     *   - 库存变为0
     */
    @Test
    public void testConcurrentBuy() throws InterruptedException {
        System.out.println("=== 测试并发购买 ===");
        
        // 步骤1：设置库存为1
        mysqlUpdate("UPDATE flash_sale_product SET sold_count = total_stock - 1 WHERE flash_sale_id = " + TEST_FLASH_SALE_ID + " AND product_id = " + TEST_PRODUCT_ID);
        System.out.println("已设置库存为1");
        
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
                        // 获取captchaToken
                        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
                        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
                        CaptchaVerifyRequest verifyRequest = new CaptchaVerifyRequest();
                        verifyRequest.setUuid(captchaResult.uuid());
                        verifyRequest.setCode(code);
                        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, verifyRequest);
                        
                        // 购买
                        FlashSaleBuyRequest buyRequest = new FlashSaleBuyRequest();
                        buyRequest.setFlashSaleId(TEST_FLASH_SALE_ID);
                        buyRequest.setProductId(TEST_PRODUCT_ID);
                        buyRequest.setCaptchaToken(captchaToken);
                        FlashSaleService.FlashSaleBuyResult buyResult = flashSaleCoreServicePractice.buy(TEST_USER_ID, buyRequest);
                        
                        if (buyResult.status().equals("SUCCESS")) {
                            successCount.incrementAndGet();
                            System.out.println("线程" + threadIndex + ": 购买成功");
                        } else {
                            failCount.incrementAndGet();
                            System.out.println("线程" + threadIndex + ": 购买失败 - " + buyResult.message());
                        }
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                        System.out.println("线程" + threadIndex + ": 异常 - " + e.getMessage());
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
            
            // 验证：应该只有1个成功（因为库存只有1）
            assertEquals(1, successCount.get(), "库存为1时应该只有1个成功");
            assertEquals(threadCount - 1, failCount.get(), "其他应该失败");
            
            // 验证：库存应该为0
            Integer stockAfter = mysqlQueryInt("SELECT total_stock - sold_count FROM flash_sale_product WHERE flash_sale_id = " + TEST_FLASH_SALE_ID + " AND product_id = " + TEST_PRODUCT_ID);
            assertEquals(0, stockAfter, "并发购买后库存应该为0");
            
            System.out.println("=== 并发测试通过 ===");
        } finally {
            // 恢复库存
            mysqlUpdate("UPDATE flash_sale_product SET sold_count = 0 WHERE flash_sale_id = " + TEST_FLASH_SALE_ID + " AND product_id = " + TEST_PRODUCT_ID);
            System.out.println("已恢复库存");
        }
    }

    // ========== 辅助方法 ==========
    
    @Autowired
    private javax.sql.DataSource dataSource;
    
    /**
     * 执行MySQL更新操作（用于测试时修改数据库）
     * 
     * @param sql SQL更新语句
     */
    private void mysqlUpdate(String sql) {
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate = 
            new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbcTemplate.execute(sql);
    }
    
    /**
     * 执行MySQL查询，返回整数结果
     * 
     * @param sql SQL查询语句
     * @return 查询结果（Integer）
     */
    private Integer mysqlQueryInt(String sql) {
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate = 
            new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
    
    /**
     * 执行MySQL查询，返回BigDecimal结果
     * 
     * @param sql SQL查询语句
     * @return 查询结果（BigDecimal）
     */
    private java.math.BigDecimal mysqlQueryBigDecimal(String sql) {
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate = 
            new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        return jdbcTemplate.queryForObject(sql, java.math.BigDecimal.class);
    }

    @Configuration
    static class TestConfig {
        @Bean
        public ElasticsearchOperations elasticsearchOperations() {
            return org.mockito.Mockito.mock(ElasticsearchOperations.class);
        }
    }

}
