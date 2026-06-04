package com.xytgy.teamallbackend.module.flashsale.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 验证码图片池：异步预生成，取用时零开销。
 * <p>
 * 应用启动时预生成 1000 张，取用后异步补充到高水位。
 * 高并发场景下避免 AWT 图片渲染阻塞请求线程。
 */
@Component
@Slf4j
public class CaptchaPool {

    @Value("${flash-sale.captcha-pool-size:1000}")
    private int poolSize;

    private static final int LOW_WATER_MARK = 200;
    private static final int REPLENISH_BATCH = 100;
    private static final int CAPTCHA_LEN = 4;
    private static final int IMG_WIDTH = 120;
    private static final int IMG_HEIGHT = 40;
    private static final int NOISE_LINES = 6;

    private final ConcurrentLinkedQueue<CaptchaEntry> pool = new ConcurrentLinkedQueue<>();
    // P2#9: 使用 AtomicBoolean 保证只有一个线程触发补充，避免重复生成
    private final java.util.concurrent.atomic.AtomicBoolean replenishing = new java.util.concurrent.atomic.AtomicBoolean(false);

    @PostConstruct
    public void init() {
        // 异步预生成，不阻塞应用启动
        Thread initThread = new Thread(this::fillPool, "captcha-pool-init");
        initThread.setDaemon(true);
        initThread.start();
    }

    /**
     * 从池中获取一个验证码，池不足时返回 null（调用方降级为实时生成）。
     */
    public CaptchaEntry poll() {
        CaptchaEntry entry = pool.poll();
        // 低于水位线时异步补充
        // CAS 保证同一时刻只有一个线程触发补充
        if (pool.size() < LOW_WATER_MARK && replenishing.compareAndSet(false, true)) {
            asyncReplenish();
        }
        return entry;
    }

    public int size() {
        return pool.size();
    }

    private void fillPool() {
        int generated = 0;
        while (generated < poolSize) {
            try {
                pool.offer(generateOne());
                generated++;
            } catch (Exception e) {
                log.warn("验证码预生成失败: {}", e.getMessage());
            }
        }
        log.info("验证码图片池初始化完成, 池大小={}", pool.size());
    }

    private void asyncReplenish() {
        Thread t = new Thread(() -> {
            try {
                for (int i = 0; i < REPLENISH_BATCH; i++) {
                    pool.offer(generateOne());
                }
            } catch (Exception e) {
                log.warn("验证码池补充失败: {}", e.getMessage());
            } finally {
                replenishing.set(false);
            }
        }, "captcha-pool-replenish");
        t.setDaemon(true);
        t.start();
    }

    private CaptchaEntry generateOne() {
        String code = randomCode();
        String base64 = renderImage(code);
        return new CaptchaEntry(code, base64);
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CAPTCHA_LEN);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < CAPTCHA_LEN; i++) {
            sb.append(r.nextInt(10));
        }
        return sb.toString();
    }

    private String renderImage(String code) {
        BufferedImage img = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, IMG_WIDTH, IMG_HEIGHT);

        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < NOISE_LINES; i++) {
            g.setColor(new Color(r.nextInt(200), r.nextInt(200), r.nextInt(200)));
            g.drawLine(r.nextInt(IMG_WIDTH), r.nextInt(IMG_HEIGHT), r.nextInt(IMG_WIDTH), r.nextInt(IMG_HEIGHT));
        }

        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(r.nextInt(150), r.nextInt(150), r.nextInt(150)));
            g.drawString(String.valueOf(code.charAt(i)), 20 + i * 25, 30);
        }
        g.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("验证码图片渲染失败", e);
        }
    }

    public record CaptchaEntry(String code, String imageBase64) {}
}
