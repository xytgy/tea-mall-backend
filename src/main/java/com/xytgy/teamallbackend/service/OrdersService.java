package com.xytgy.teamallbackend.service;

import com.xytgy.teamallbackend.entity.Orders;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.dto.OrderCreateRequest;
import com.xytgy.teamallbackend.vo.CreateOrderVO;
import com.xytgy.teamallbackend.vo.OrderVO;

import java.util.List;

/**
* @author xytgy
* @description 针对表【orders】的数据库操作Service
* @createDate 2026-04-15 08:01:03
*/
public interface OrdersService extends IService<Orders> {
    CreateOrderVO createOrder(Long userId, OrderCreateRequest request);
    List<OrderVO> listOrders(Long userId);
    void confirmOrder(Long userId, Long orderId);
    void cancelOrder(Long userId, Long orderId);
}
