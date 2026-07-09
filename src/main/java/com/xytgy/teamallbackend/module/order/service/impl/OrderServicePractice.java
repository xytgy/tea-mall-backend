package com.xytgy.teamallbackend.module.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xytgy.teamallbackend.module.order.dto.OrderCreateRequest;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.order.mapper.OrdersMapper;
import com.xytgy.teamallbackend.module.order.vo.CreateOrderVO;
import com.xytgy.teamallbackend.module.product.entity.Product;
import com.xytgy.teamallbackend.module.product.mapper.ProductMapper;
import com.xytgy.teamallbackend.mq.message.order.OrderTimeoutMessage;
import com.xytgy.teamallbackend.mq.publisher.OrderTimeoutPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServicePractice {

    private final OrdersMapper ordersMapper;
    private final ProductMapper productMapper;
    private final OrderTimeoutPublisher orderTimeoutPublisher;

    @Transactional
    public CreateOrderVO createOrder(Long userId, OrderCreateRequest request) {
        // 取第一个商品（练习版只支持单件）
        OrderCreateRequest.Item item = request.getItems().get(0);
        Long productId = item.getProductId();
        int quantity = item.getQuantity();

        // 1. 校验商品
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }
        if (product.getStock() < quantity) {
            throw new RuntimeException("库存不足");
        }

        // 2. 扣库存（乐观锁）
        int affected = productMapper.update(null, new UpdateWrapper<Product>()
                .eq("id", productId)
                .apply("stock >= " + quantity)
                .setSql("stock = stock - " + quantity));
        if (affected == 0) {
            throw new RuntimeException("库存扣减失败");
        }

        // 3. 创建订单（状态0：待支付）
        Orders order = new Orders();
        order.setOrderNo(UUID.randomUUID().toString().replace("-", ""));
        order.setUserId(userId);
        order.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        order.setStatus(Orders.STATUS_PENDING_PAYMENT);
        order.setReceiverName(request.getReceiverName());
        order.setReceiverPhone(request.getReceiverPhone());
        order.setReceiverAddress(request.getReceiverAddress());
        order.setSource(Orders.SOURCE_NORMAL);
        order.setCreateTime(LocalDateTime.now());
        ordersMapper.insert(order);

        // 4. 发送30分钟超时取消消息
        OrderTimeoutMessage timeoutMessage = OrderTimeoutMessage.builder()
                .orderId(order.getId())
                .userId(userId)
                .build();
        orderTimeoutPublisher.publishOrderTimeout(timeoutMessage);

        log.info("订单创建成功: orderId={}, orderNo={}", order.getId(), order.getOrderNo());
        return new CreateOrderVO(order.getOrderNo(), order.getId());
    }

    // 支付订单
    public void payOrder(Long orderId, Long userId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此订单");
        }
        if (!order.getStatus().equals(Orders.STATUS_PENDING_PAYMENT)) {
            throw new RuntimeException("订单状态不允许支付");
        }

        // 模拟支付成功，更新状态0→1
        order.setStatus(Orders.STATUS_PAID);
        order.setPayTime(LocalDateTime.now());
        ordersMapper.updateById(order);
        log.info("订单支付成功: orderId={}", orderId);
    }

    // 取消订单（练习版简化：不恢复库存，生产版需要查order_item表）
    public void cancelOrder(Long orderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        if (!order.getStatus().equals(Orders.STATUS_PENDING_PAYMENT)) {
            return;
        }

        // 更新状态0→4
        order.setStatus(Orders.STATUS_CANCELLED);
        ordersMapper.updateById(order);
        log.info("订单超时取消: orderId={}", orderId);
    }
}
