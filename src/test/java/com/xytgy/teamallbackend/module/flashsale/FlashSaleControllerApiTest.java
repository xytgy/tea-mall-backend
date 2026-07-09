package com.xytgy.teamallbackend.module.flashsale;

import com.xytgy.teamallbackend.module.flashsale.dto.CaptchaVerifyRequest;
import com.xytgy.teamallbackend.module.flashsale.service.FlashSaleCoreServicePractice;
import com.xytgy.teamallbackend.module.flashsale.service.FlashSaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * FlashSaleController API测试
 * 测试HTTP请求和响应
 */
@SpringBootTest
@AutoConfigureMockMvc
public class FlashSaleControllerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FlashSaleCoreServicePractice flashSaleCoreServicePractice;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final Long TEST_USER_ID = 17L;

    @BeforeEach
    public void setUp() {
        // 确保活动时间和库存正常
    }

    // ========== 测试获取验证码接口 ==========

    /**
     * API测试：测试获取验证码接口
     * 
     * 测试目的：验证GET /api/flash-sale/captchapratice接口是否正常工作
     * 测试方式：使用MockMvc模拟HTTP请求
     * 测试范围：测试Controller层的HTTP请求和响应
     * 
     * 测试步骤：
     *   1. 发送GET请求到验证码接口
     *   2. 带上JWT Token进行认证
     *   3. 验证响应状态码为200
     *   4. 验证响应数据格式正确
     * 
     * 预期结果：
     *   - HTTP状态码：200 OK
     *   - success字段：true
     *   - data.uuid：不为空
     *   - data.imageBase64：以"data:image/png;base64,"开头
     */
    @Test
    public void testGetCaptcha_API() throws Exception {
        mockMvc.perform(get("/api/flash-sale/captchapratice")
                .header("Authorization", "Bearer " + getJwtToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uuid").isNotEmpty())
                .andExpect(jsonPath("$.data.imageBase64").value(startsWith("data:image/png;base64,")));
    }

    @Test
    public void testGetCaptchaWithoutAuth_API() throws Exception {
        mockMvc.perform(get("/api/flash-sale/captchapratice"))
                .andExpect(status().isUnauthorized());
    }

    // ========== 测试验证验证码接口 ==========

    @Test
    public void testVerifyCaptcha_API() throws Exception {
        // 先获取验证码
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());

        CaptchaVerifyRequest request = new CaptchaVerifyRequest();
        request.setUuid(captchaResult.uuid());
        request.setCode(code);

        mockMvc.perform(post("/api/flash-sale/captcha/verifyPratice")
                .header("Authorization", "Bearer " + getJwtToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uuid\":\"" + captchaResult.uuid() + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    public void testVerifyCaptchaWithWrongCode_API() throws Exception {
        // 先获取验证码
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);

        mockMvc.perform(post("/api/flash-sale/captcha/verifyPratice")
                .header("Authorization", "Bearer " + getJwtToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uuid\":\"" + captchaResult.uuid() + "\",\"code\":\"0000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("验证失败"));
    }

    // ========== 测试购买接口 ==========

    @Test
    public void testBuy_API() throws Exception {
        // 先获取captchaToken
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, 
            new CaptchaVerifyRequest() {{ setUuid(captchaResult.uuid()); setCode(code); }});

        mockMvc.perform(post("/api/flash-sale/buypratice")
                .header("Authorization", "Bearer " + getJwtToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"flashSaleId\":1,\"productId\":9,\"captchaToken\":\"" + captchaToken + "\",\"deviceFingerprint\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    public void testBuyWithoutAuth_API() throws Exception {
        mockMvc.perform(post("/api/flash-sale/buypratice")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"flashSaleId\":1,\"productId\":9,\"captchaToken\":\"test\",\"deviceFingerprint\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testBuyWithExpiredToken_API() throws Exception {
        mockMvc.perform(post("/api/flash-sale/buypratice")
                .header("Authorization", "Bearer " + getJwtToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"flashSaleId\":1,\"productId\":9,\"captchaToken\":\"expired-token\",\"deviceFingerprint\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAIL"))
                .andExpect(jsonPath("$.data.message").value("验证码无效或已过期"));
    }

    @Test
    public void testBuyWithNonExistActivity_API() throws Exception {
        // 先获取captchaToken
        FlashSaleService.CaptchaResult captchaResult = flashSaleCoreServicePractice.generateCaptchaPractice(TEST_USER_ID);
        String code = stringRedisTemplate.opsForValue().get("captcha:" + captchaResult.uuid());
        String captchaToken = flashSaleCoreServicePractice.verifyCaptchaPractice(TEST_USER_ID, 
            new CaptchaVerifyRequest() {{ setUuid(captchaResult.uuid()); setCode(code); }});

        mockMvc.perform(post("/api/flash-sale/buypratice")
                .header("Authorization", "Bearer " + getJwtToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"flashSaleId\":999,\"productId\":9,\"captchaToken\":\"" + captchaToken + "\",\"deviceFingerprint\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAIL"))
                .andExpect(jsonPath("$.data.message").value("活动没有开始或者不存在"));
    }

    /**
     * 获取JWT Token（简化版，实际应该调用登录接口）
     */
    private String getJwtToken() {
        // 在实际测试中，应该调用登录接口获取token
        // 这里简化处理，返回一个固定的token
        try {
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userAccount\":\"testuser001\",\"password\":\"Test@12345\"}"))
                    .andReturn();
            String response = result.getResponse().getContentAsString();
            // 解析token（简化处理）
            if (response.contains("accessToken")) {
                int start = response.indexOf("accessToken\":\"") + 14;
                int end = response.indexOf("\"", start);
                return response.substring(start, end);
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return "invalid-token";
    }
}
