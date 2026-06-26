package com.xytgy.teamallbackend.module.flashsale.service;

import com.xytgy.teamallbackend.module.flashsale.dto.CaptchaVerifyRequest;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleAddProductRequest;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleBuyRequest;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleCreateRequest;
import com.xytgy.teamallbackend.module.flashsale.vo.FlashSaleProductVO;
import com.xytgy.teamallbackend.module.flashsale.vo.FlashSaleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 秒杀门面类，对外保持 FlashSaleService 接口不变，
 * 内部委托给三个职责单一的子 Service。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FlashSaleServiceImpl implements FlashSaleService {

    private final FlashSaleCoreService coreService;
    private final FlashSaleCaptchaService captchaService;
    private final FlashSaleAdminService adminService;

    // ========== 核心秒杀链路 ==========

    @Override
    public FlashSaleBuyResult buy(Long userId, FlashSaleBuyRequest request) {
        return coreService.buy(userId, request);
    }

    @Override
    public void warmup(Long flashSaleId, Long operatorId) {
        coreService.warmup(flashSaleId, operatorId);
    }

    @Override
    public void restock(Long productId, Integer quantity, Long operatorId) {
        coreService.restock(productId, quantity, operatorId);
    }

    @Override
    public void cleanup(Long flashSaleId, Long operatorId) {
        coreService.cleanup(flashSaleId, operatorId);
    }

    // ========== 验证码 ==========

    @Override
    public CaptchaResult generateCaptcha(Long userId) {
        return captchaService.generateCaptcha(userId);
    }

    @Override
    public String verifyCaptcha(Long userId, CaptchaVerifyRequest request) {
        return captchaService.verifyCaptcha(userId, request);
    }

    // ========== 管理 / 查询 / 申诉 / 补偿 ==========

    @Override
    public List<FlashSaleVO> listActiveSales() {
        return adminService.listActiveSales();
    }

    @Override
    public List<FlashSaleProductVO> getProducts(Long flashSaleId) {
        return adminService.getProducts(flashSaleId);
    }

    @Override
    public Map<String, Object> getOrderResult(Long userId, Long orderId) {
        return adminService.getOrderResult(userId, orderId);
    }

    @Override
    public FlashSaleBuyResult appeal(Long userId, Long flashSaleId) {
        return adminService.appeal(userId, flashSaleId);
    }

    @Override
    public Map<String, Object> getFailedOrders(int page, int size) {
        return adminService.getFailedOrders(page, size);
    }

    @Override
    public void retryFailedOrder(Long failedOrderId, Long operatorId) {
        adminService.retryFailedOrder(failedOrderId, operatorId);
    }

    @Override
    public void cancelFailedOrder(Long failedOrderId, Long operatorId) {
        adminService.cancelFailedOrder(failedOrderId, operatorId);
    }

    @Override
    public void compensateFailedOrder(Long failedOrderId, BigDecimal amount, String remark, Long operatorId) {
        adminService.compensateFailedOrder(failedOrderId, amount, remark, operatorId);
    }

    @Override
    public void manualProcessFailedOrder(Long failedOrderId, String remark, Long operatorId) {
        adminService.manualProcessFailedOrder(failedOrderId, remark, operatorId);
    }

    @Override
    public List<FlashSaleVO> listAllSales() {
        return adminService.listAllSales();
    }

    @Override
    public void updateRateConfig(Long frequentThreshold, Long maliciousThreshold, Long blacklistMinutes, Long operatorId) {
        adminService.updateRateConfig(frequentThreshold, maliciousThreshold, blacklistMinutes, operatorId);
    }

    @Override
    public Map<String, Object> getRateConfig() {
        return adminService.getRateConfig();
    }

    @Override
    public void addWhitelist(Long flashSaleId, List<Long> userIds, Long operatorId) {
        adminService.addWhitelist(flashSaleId, userIds, operatorId);
    }

    // ========== CRUD 管理 ==========

    @Override
    public Long createFlashSale(FlashSaleCreateRequest request, Long operatorId) {
        return adminService.createFlashSale(request, operatorId);
    }

    @Override
    public void addProduct(Long flashSaleId, FlashSaleAddProductRequest request, Long operatorId) {
        adminService.addProduct(flashSaleId, request, operatorId);
    }

    @Override
    public void updateStatus(Long flashSaleId, Integer status, Long operatorId) {
        adminService.updateStatus(flashSaleId, status, operatorId);
    }

    @Override
    public FlashSaleVO getDetail(Long flashSaleId) {
        return adminService.getDetail(flashSaleId);
    }

    @Override
    public void deleteFlashSale(Long flashSaleId, Long operatorId) {
        adminService.deleteFlashSale(flashSaleId, operatorId);
    }
}
