package com.xytgy.teamallbackend.module.flashsale.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.code.kaptcha.Producer;
import com.xytgy.teamallbackend.module.flashsale.dto.CaptchaVerifyRequest;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleBuyRequest;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSale;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleProduct;
import com.xytgy.teamallbackend.module.flashsale.mapper.FlashSaleMapper;
import com.xytgy.teamallbackend.module.flashsale.mapper.FlashSaleProductMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FlashSaleCoreServicePractice 单元测试
 * 使用Mockito Mock外部依赖，测试单个方法的逻辑
 */
@ExtendWith(MockitoExtension.class)
public class FlashSaleCoreServicePracticeUnitTest {

    @InjectMocks
    private FlashSaleCoreServicePractice flashSaleCoreServicePractice;

    @Mock
    private FlashSaleMapper flashSaleMapper;

    @Mock
    private FlashSaleProductMapper flashSaleProductMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private Producer kaptchaProducer;

    @Mock
    private ValueOperations<String, String> valueOperations;

    // ========== 测试验证码生成 ==========

    @Test
    public void testGenerateCaptcha_Unit() {
        // 准备Mock数据
        String mockCode = "3847";
        BufferedImage mockImage = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB);
        
        when(kaptchaProducer.createText()).thenReturn(mockCode);
        when(kaptchaProducer.createImage(mockCode)).thenReturn(mockImage);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        
        // 执行测试
        FlashSaleService.CaptchaResult result = flashSaleCoreServicePractice.generateCaptchaPractice(17L);
        
        // 验证结果
        assertNotNull(result.uuid(), "UUID不能为空");
        assertNotNull(result.imageBase64(), "图片Base64不能为空");
        assertTrue(result.imageBase64().startsWith("data:image/png;base64,"), "图片格式必须正确");
        
        // 验证Redis操作
        verify(valueOperations).set(
            argThat(key -> key.startsWith("captcha:")),
            eq(mockCode),
            eq(60L),
            eq(TimeUnit.SECONDS)
        );
    }

    // ========== 测试验证码验证 ==========

    @Test
    public void testVerifyCaptcha_Success_Unit() {
        // 准备Mock数据
        String uuid = "test-uuid";
        String code = "3847";
        String userId = "17";
        
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("captcha:" + uuid)).thenReturn(code);
        
        // 执行测试
        CaptchaVerifyRequest request = new CaptchaVerifyRequest();
        request.setUuid(uuid);
        request.setCode(code);
        
        String result = flashSaleCoreServicePractice.verifyCaptchaPractice(17L, request);
        
        // 验证结果
        assertNotNull(result, "captchaToken不能为空");
        assertFalse(result.equals("验证失败"), "不应该返回验证失败");
        assertFalse(result.equals("验证码已过期"), "不应该返回验证码已过期");
        
        // 验证Redis操作
        verify(stringRedisTemplate).delete("captcha:" + uuid);
        verify(valueOperations).set(
            argThat(key -> key.startsWith("captcha_token:")),
            eq(userId),
            eq(15L),
            eq(TimeUnit.SECONDS)
        );
    }

    @Test
    public void testVerifyCaptcha_Expired_Unit() {
        // 准备Mock数据
        String uuid = "test-uuid";
        String code = "3847";
        
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("captcha:" + uuid)).thenReturn(null);  // 验证码已过期
        
        // 执行测试
        CaptchaVerifyRequest request = new CaptchaVerifyRequest();
        request.setUuid(uuid);
        request.setCode(code);
        
        String result = flashSaleCoreServicePractice.verifyCaptchaPractice(17L, request);
        
        // 验证结果
        assertEquals("验证码已过期", result, "应该返回验证码已过期");
    }

    @Test
    public void testVerifyCaptcha_WrongCode_Unit() {
        // 准备Mock数据
        String uuid = "test-uuid";
        String correctCode = "3847";
        String wrongCode = "0000";
        
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("captcha:" + uuid)).thenReturn(correctCode);
        
        // 执行测试
        CaptchaVerifyRequest request = new CaptchaVerifyRequest();
        request.setUuid(uuid);
        request.setCode(wrongCode);
        
        String result = flashSaleCoreServicePractice.verifyCaptchaPractice(17L, request);
        
        // 验证结果
        assertEquals("验证失败", result, "应该返回验证失败");
        
        // 验证没有删除验证码
        verify(stringRedisTemplate, never()).delete("captcha:" + uuid);
    }

    // ========== 测试购买方法 ==========

    @Test
    public void testBuy_InvalidCaptchaToken_Unit() {
        // 准备Mock数据
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("captcha_token:invalid-token")).thenReturn(null);
        
        // 执行测试
        FlashSaleBuyRequest request = new FlashSaleBuyRequest();
        request.setFlashSaleId(1L);
        request.setProductId(9L);
        request.setCaptchaToken("invalid-token");
        
        FlashSaleService.FlashSaleBuyResult result = flashSaleCoreServicePractice.buy(17L, request);
        
        // 验证结果
        assertEquals("FAIL", result.status(), "应该返回FAIL");
        assertEquals("验证码无效或已过期", result.message(), "应该返回验证码无效或已过期");
    }

    @Test
    public void testBuy_ActivityNotExist_Unit() {
        // 准备Mock数据
        String captchaToken = "valid-token";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("captcha_token:" + captchaToken)).thenReturn("17");
        when(flashSaleMapper.selectById(999L)).thenReturn(null);
        
        // 执行测试
        FlashSaleBuyRequest request = new FlashSaleBuyRequest();
        request.setFlashSaleId(999L);
        request.setProductId(9L);
        request.setCaptchaToken(captchaToken);
        
        FlashSaleService.FlashSaleBuyResult result = flashSaleCoreServicePractice.buy(17L, request);
        
        // 验证结果
        assertEquals("FAIL", result.status(), "应该返回FAIL");
        assertEquals("活动没有开始或者不存在", result.message(), "应该返回活动没有开始或者不存在");
    }
}
