package com.xytgy.teamallbackend.module.flashsale.service;

import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleBuyRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FlashSaleCoreServicePractice {
    @Autowired
    private FlashSaleCoreServicePractice flashSaleCoreServicePractice;

    @Test
    public void buypractice() {
        FlashSaleBuyRequest request = new FlashSaleBuyRequest();
        request.setProductId(9L);
        request.setFlashSaleId(1L);
        request.setCaptchaToken("test-token");
        FlashSaleService.FlashSaleBuyResult result = flashSaleCoreServicePractice.buypractice(17L, request);
    }
}
