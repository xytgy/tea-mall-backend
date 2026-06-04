package com.xytgy.teamallbackend.module.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.module.order.dto.OrderCreateRequest;
import com.xytgy.teamallbackend.module.order.dto.OrderPayRequest;
import com.xytgy.teamallbackend.module.order.dto.OrderReviewRequest;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.order.vo.CreateOrderVO;
import com.xytgy.teamallbackend.module.order.vo.LogisticsVO;
import com.xytgy.teamallbackend.module.order.vo.MerchantOrderVO;
import com.xytgy.teamallbackend.module.order.vo.OrderStatsVO;
import com.xytgy.teamallbackend.module.order.vo.OrderVO;

import com.xytgy.teamallbackend.common.PageResult;

import java.util.List;

/**
* @author xytgy
* @description 针对表【orders】的数据库操作Service
* @createDate 2026-04-15 08:01:03
*/
public interface OrdersService extends IService<Orders> {
    CreateOrderVO createOrder(Long userId, OrderCreateRequest request);
    PageResult<OrderVO> listOrders(Long userId, Integer status, int page, int pageSize);
    OrderVO getOrderDetail(Long userId, Long orderId);
    void confirmOrder(Long userId, Long orderId);
    void cancelOrder(Long userId, Long orderId);
    void payOrder(Long userId, OrderPayRequest request);
    void applyRefund(Long userId, Long orderId);
    void submitReview(Long userId, OrderReviewRequest request);

    PageResult<MerchantOrderVO> listMerchantOrders(Long merchantId, int page, int pageSize);
    void deliverOrder(Long merchantId, Long orderId);
    OrderStatsVO getOrderStats(Long userId);
    List<LogisticsVO> getOrderLogistics(Long userId, Long orderId);

    void approveRefund(Long merchantId, Long orderId);
    void refuseRefund(Long merchantId, Long orderId, String reason);

    // S6809: 供自注入代理调用的事务方法，确保事务生效
    void doCancelOrderInTransaction(Orders order);
    void doApproveRefundInTransaction(Orders order);
}
