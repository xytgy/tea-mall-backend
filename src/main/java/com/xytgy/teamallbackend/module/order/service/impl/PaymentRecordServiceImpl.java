package com.xytgy.teamallbackend.module.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.module.order.entity.PaymentRecord;
import com.xytgy.teamallbackend.module.order.mapper.PaymentRecordMapper;
import com.xytgy.teamallbackend.module.order.service.PaymentRecordService;
import org.springframework.stereotype.Service;

@Service
public class PaymentRecordServiceImpl extends ServiceImpl<PaymentRecordMapper, PaymentRecord> implements PaymentRecordService {

    @Override
    public PaymentRecord getByOutTradeNo(String outTradeNo) {
        return this.getOne(new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getOutTradeNo, outTradeNo));
    }

    @Override
    public PaymentRecord getLastPayingRecord(Long orderId) {
        return this.getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, orderId)
                .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_PAYING)
                .orderByDesc(PaymentRecord::getCreateTime)
                .last("LIMIT 1"));
    }

    @Override
    public PaymentRecord getLastPaidRecord(Long orderId) {
        return this.getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, orderId)
                .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_PAID)
                .orderByDesc(PaymentRecord::getPayTime)
                .orderByDesc(PaymentRecord::getId)
                .last("LIMIT 1"));
    }
}
