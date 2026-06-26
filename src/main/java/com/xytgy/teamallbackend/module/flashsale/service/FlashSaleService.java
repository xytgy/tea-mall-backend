package com.xytgy.teamallbackend.module.flashsale.service;

import com.xytgy.teamallbackend.module.flashsale.dto.CaptchaVerifyRequest;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleAddProductRequest;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleBuyRequest;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleCreateRequest;
import com.xytgy.teamallbackend.module.flashsale.vo.FlashSaleProductVO;
import com.xytgy.teamallbackend.module.flashsale.vo.FlashSaleVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface FlashSaleService {

    List<FlashSaleVO> listActiveSales();

    List<FlashSaleProductVO> getProducts(Long flashSaleId);

    CaptchaResult generateCaptcha(Long userId);

    String verifyCaptcha(Long userId, CaptchaVerifyRequest request);

    FlashSaleBuyResult buy(Long userId, FlashSaleBuyRequest request);

    Map<String, Object> getOrderResult(Long userId, Long orderId);

    void warmup(Long flashSaleId, Long operatorId);

    void restock(Long productId, Integer quantity, Long operatorId);

    void cleanup(Long flashSaleId, Long operatorId);

    void addWhitelist(Long flashSaleId, List<Long> userIds, Long operatorId);

    FlashSaleBuyResult appeal(Long userId, Long flashSaleId);

    Map<String, Object> getFailedOrders(int page, int size);

    void retryFailedOrder(Long failedOrderId, Long operatorId);

    void cancelFailedOrder(Long failedOrderId, Long operatorId);

    void updateRateConfig(Long frequentThreshold, Long maliciousThreshold, Long blacklistMinutes, Long operatorId);

    Map<String, Object> getRateConfig();

    void compensateFailedOrder(Long failedOrderId, BigDecimal amount, String remark, Long operatorId);

    List<FlashSaleVO> listAllSales();


    void manualProcessFailedOrder(Long failedOrderId, String remark, Long operatorId);

    Long createFlashSale(FlashSaleCreateRequest request, Long operatorId);

    void addProduct(Long flashSaleId, FlashSaleAddProductRequest request, Long operatorId);

    void updateStatus(Long flashSaleId, Integer status, Long operatorId);

    FlashSaleVO getDetail(Long flashSaleId);

    void deleteFlashSale(Long flashSaleId, Long operatorId);

    record CaptchaResult(String uuid, String imageBase64) {}

    record FlashSaleBuyResult(String status, Long orderId, String message) {}
}
