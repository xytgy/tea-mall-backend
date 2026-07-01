package com.xytgy.teamallbackend.module.flashsale.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.google.code.kaptcha.Producer;
import com.xytgy.teamallbackend.module.flashsale.dto.CaptchaVerifyRequest;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleBuyRequest;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSale;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleProduct;
import com.xytgy.teamallbackend.module.flashsale.mapper.FlashSaleMapper;
import com.xytgy.teamallbackend.module.flashsale.mapper.FlashSaleProductMapper;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.order.mapper.OrdersMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Service
@AllArgsConstructor
@Slf4j
public class FlashSaleCoreServicePractice {

    private static final String CAPTCHA_KEY_PREFIX = "captcha:";
    private static final String CAPTCHA_TOKEN_KEY_PREFIX = "captcha_token:";
    private static final long CAPTCHA_EXPIRE_SECONDS = 60;
    private static final long TOKEN_EXPIRE_SECONDS = 15;

    private final FlashSaleMapper flashSaleMapper;
    private final FlashSaleProductMapper flashSaleProductMapper;
    private final OrdersMapper ordersMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final Producer kaptchaProducer;

    //这个是返回图片和UUID为之后的验证还有生成还有返回验证token做前置准备
    public FlashSaleService.CaptchaResult generateCaptchaPractice(Long userId) {
        //先创建uuid
        String uuid = UUID.randomUUID().toString().replace("-","");
        //之后生成code,配置文KaptchaConfig件已经写好了,用生成的code来生成图片
        String code = kaptchaProducer.createText();
        BufferedImage bufferedImage = kaptchaProducer.createImage(code);
        //uuid作为一个key，code作为一个value存储到redis里，
        //还有就是，要把图片进行转换然后把uuid和转换之后的图片发给前端
        String key = CAPTCHA_KEY_PREFIX + uuid;
        stringRedisTemplate.opsForValue().set(key, code, CAPTCHA_EXPIRE_SECONDS, TimeUnit.SECONDS);
        String base64 = bufferedImageToBase64(bufferedImage);
        return new FlashSaleService.CaptchaResult(uuid,base64);
    }

    //图片进行转换base64
    private String bufferedImageToBase64(BufferedImage bufferedImage) {
        if (bufferedImage == null) {
            return null;
        }

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage,"png",bos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            log.error("图片内容写入失败: {}", e.getMessage(), e);
            throw new RuntimeException("验证码图片生成失败", e);
        }
    }


    //这个是秒杀减库存的方法，
    public FlashSaleService.FlashSaleBuyResult buy(Long userId, FlashSaleBuyRequest request) {
        // 校验captchaToken（防刷机制）
        String captchaToken = request.getCaptchaToken();
        String tokenKey = CAPTCHA_TOKEN_KEY_PREFIX + captchaToken;
        String tokenUserId = stringRedisTemplate.opsForValue().get(tokenKey);
        if (tokenUserId == null) {
            return new FlashSaleService.FlashSaleBuyResult("FAIL", null, "验证码无效或已过期");
        }
        // 校验token是否属于当前用户
        if (!String.valueOf(userId).equals(tokenUserId)) {
            return new FlashSaleService.FlashSaleBuyResult("FAIL", null, "验证码无效");
        }
        // 删除captchaToken（一次性使用，防止重放）
        stringRedisTemplate.delete(tokenKey);

        Long flashSaleId = request.getFlashSaleId();
        if (validateActivity(flashSaleId) == null) {
            return new FlashSaleService.FlashSaleBuyResult("FAIL", null, "活动没有开始或者不存在");
        }
        FlashSaleProduct flashSaleProduct = validateProduct(request.getProductId());
        if (flashSaleProduct == null) {
            return new FlashSaleService.FlashSaleBuyResult("FAIL", null, "商品不存在");
        }
        Long dbFlashSaleId = flashSaleProduct.getFlashSaleId();
        // 检查活动是否存在这个商品
        if (!flashSaleId.equals(dbFlashSaleId)) {
            return new FlashSaleService.FlashSaleBuyResult("FAIL", null, "该商品不属于该活动");
        }

        int affected = flashSaleProductMapper.update(null, new UpdateWrapper<FlashSaleProduct>()
                .eq("flash_sale_id", flashSaleId)
                .eq("product_id", request.getProductId())
                .apply("total_stock - sold_count > 0")
                .setSql("sold_count = sold_count + 1"));
        if (affected == 0) {
            return new FlashSaleService.FlashSaleBuyResult("FAIL", null, "库存不足");
        }

        //创建订单，插入到数据库里，因为这个文件是练习所以还有收件人的姓名之类的没有实现
        Orders order = new Orders();
        order.setUserId(userId);
        order.setOrderNo(UUID.randomUUID().toString());
        order.setStatus(0);
        order.setTotalAmount(flashSaleProduct.getFlashPrice());
        order.setSource(1);
        order.setCreateTime(LocalDateTime.now());
        ordersMapper.insert(order);
        return new FlashSaleService.FlashSaleBuyResult("SUCCESS", order.getId(), "抢购成功");
    }

    // 验证活动是否存在，和检查活动是否没有开始或已经结束
    private FlashSale validateActivity(Long flashSaleId) {
        FlashSale flashSale = flashSaleMapper.selectById(flashSaleId);
        if (flashSale == null) {
            return null;
        }
        Integer status = flashSale.getStatus();
        if (status != 1) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(flashSale.getEndTime()) || now.isBefore(flashSale.getStartTime())) {
            return null;
        }
        return flashSale;
    }

    // 检查商品是否存在
    private FlashSaleProduct validateProduct(Long productId) {
        FlashSaleProduct flashSaleProduct = flashSaleProductMapper.selectOne(new QueryWrapper<FlashSaleProduct>()
                .eq("product_id", productId));
        if (flashSaleProduct == null) {
            return null;
        }
        return flashSaleProduct;
    }



    public String verifyCaptchaPractice(Long userId, CaptchaVerifyRequest request) {
        String strId = String.valueOf(userId);
        String uuid = request.getUuid();
        String key = CAPTCHA_KEY_PREFIX + uuid;
        String code = stringRedisTemplate.opsForValue().get(key);
        if (code == null) {
            return "验证码已过期";
        }
        if (request.getCode().equalsIgnoreCase(code)) {
            stringRedisTemplate.delete(key);
            String captchaToken = UUID.randomUUID().toString();
            String tokenKey = CAPTCHA_TOKEN_KEY_PREFIX + captchaToken;
            stringRedisTemplate.opsForValue().set(tokenKey, strId, TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);
            return captchaToken;
        }
        return "验证失败";
    }
}
