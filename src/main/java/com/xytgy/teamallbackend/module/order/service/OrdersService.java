package com.xytgy.teamallbackend.module.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.module.order.dto.OrderCreateRequest;
import com.xytgy.teamallbackend.module.order.dto.OrderPayRequest;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.order.vo.CreateOrderVO;
import com.xytgy.teamallbackend.module.order.vo.MerchantOrderVO;
import com.xytgy.teamallbackend.module.order.vo.OrderStatsVO;
import com.xytgy.teamallbackend.module.order.vo.OrderVO;

import java.util.List;

/**
* @author xytgy
* @description 针对表【orders】的数据库操作Service
* @createDate 2026-04-15 08:01:03
*/
public interface OrdersService extends IService<Orders> {
    CreateOrderVO createOrder(Long userId, OrderCreateRequest request);
    List<OrderVO> listOrders(Long userId, Integer status);
    OrderVO getOrderDetail(Long userId, Long orderId);
    void confirmOrder(Long userId, Long orderId);
    void cancelOrder(Long userId, Long orderId);
    void payOrder(Long userId, OrderPayRequest request);
    
    List<MerchantOrderVO> listMerchantOrders(Long merchantId);
    void deliverOrder(Long merchantId, Long orderId);
    OrderStatsVO getOrderStats(Long userId);
}
