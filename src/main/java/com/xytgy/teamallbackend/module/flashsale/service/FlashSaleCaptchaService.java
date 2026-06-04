package com.xytgy.teamallbackend.module.flashsale.service;

import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.flashsale.dto.CaptchaVerifyRequest;
import com.xytgy.teamallbackend.module.flashsale.service.FlashSaleService.CaptchaResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlashSaleCaptchaService {

    private final CaptchaPool captchaPool;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final String CAPTCHA_TOKEN_PREFIX = "captcha_token:";

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
}
