package com.xytgy.teamallbackend.module.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.module.order.entity.PaymentRecord;

public interface PaymentRecordService extends IService<PaymentRecord> {
    PaymentRecord getByOutTradeNo(String outTradeNo);
    PaymentRecord getLastPayingRecord(Long orderId);
}