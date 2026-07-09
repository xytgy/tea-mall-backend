package com.xytgy.teamallbackend.module.order.service;

import com.xytgy.teamallbackend.module.order.dto.OrderCreateRequest;
import com.xytgy.teamallbackend.module.order.service.impl.OrderServicePractice;
import com.xytgy.teamallbackend.module.order.vo.CreateOrderVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
public class OrderServicePracticeTest {

    @Autowired
    private OrderServicePractice orderServicePractice;

    // ========== 正向测试 ==========

    @Test
    public void testCreateOrder_Success() {
        CreateOrderVO result = orderServicePractice.createOrder(17L, buildValidRequest());
        assertNotNull(result, "返回结果不能为空");
        assertNotNull(result.getOrderNo(), "订单号不能为空");
        assertNotNull(result.getOrderId(), "订单ID不能为空");
        System.out.println("订单创建成功: orderNo=" + result.getOrderNo());
    }

    @Test
    public void testPayOrder_Success() {
        CreateOrderVO order = createTestOrder();
        orderServicePractice.payOrder(order.getOrderId(), 17L);
        System.out.println("订单支付成功: orderId=" + order.getOrderId());
    }

    // ========== 业务逻辑测试 ==========

    @Test
    public void testCreateOrder_ProductNotExist() {
        OrderCreateRequest request = buildValidRequest();
        request.getItems().get(0).setProductId(999L);
        assertThrows(RuntimeException.class,
                () -> orderServicePractice.createOrder(17L, request),
                "商品不存在时应抛异常");
    }

    @Test
    public void testCreateOrder_StockNotEnough() {
        OrderCreateRequest request = buildValidRequest();
        request.getItems().get(0).setQuantity(99999);
        assertThrows(RuntimeException.class,
                () -> orderServicePractice.createOrder(17L, request),
                "库存不足时应抛异常");
    }

    @Test
    public void testPayOrder_WrongStatus() {
        CreateOrderVO order = createTestOrder();
        orderServicePractice.payOrder(order.getOrderId(), 17L);
        assertThrows(RuntimeException.class,
                () -> orderServicePractice.payOrder(order.getOrderId(), 17L),
                "重复支付时应抛异常");
    }

    // ========== 辅助方法 ==========

    private OrderCreateRequest buildValidRequest() {
        OrderCreateRequest.Item item = new OrderCreateRequest.Item();
        item.setProductId(9L);
        item.setQuantity(1);

        OrderCreateRequest request = new OrderCreateRequest();
        request.setItems(Collections.singletonList(item));
        request.setReceiverName("张三");
        request.setReceiverPhone("13800138000");
        request.setReceiverAddress("北京市朝阳区");
        return request;
    }

    private CreateOrderVO createTestOrder() {
        return orderServicePractice.createOrder(17L, buildValidRequest());
    }
}
