package com.xytgy.teamallbackend.module.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.mapstruct.CopyMapper;
import com.xytgy.teamallbackend.module.order.dto.OrderCreateRequest;
import com.xytgy.teamallbackend.module.order.dto.OrderPayRequest;
import com.xytgy.teamallbackend.module.order.entity.OrderItem;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.product.entity.Product;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.order.repository.OrdersMapper;
import com.xytgy.teamallbackend.module.cart.service.CartService;
import com.xytgy.teamallbackend.module.order.service.OrderItemService;
import com.xytgy.teamallbackend.module.order.service.OrdersService;
import com.xytgy.teamallbackend.module.product.service.ProductService;
import com.xytgy.teamallbackend.module.order.vo.CreateOrderVO;
import com.xytgy.teamallbackend.module.order.vo.MerchantOrderVO;
import com.xytgy.teamallbackend.module.order.vo.OrderItemVO;
import com.xytgy.teamallbackend.module.order.vo.OrderVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
/**
* @author xytgy
* @description 针对表【orders】的数据库操作Service实现
* @createDate 2026-04-15 08:01:03
*/
@Service
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper, Orders>
    implements OrdersService{

    @Autowired
    private ProductService productService;
    @Autowired
    private OrderItemService orderItemService;
    @Autowired
    private CartService cartService;
    
    @Autowired
    private CopyMapper copyMapper;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateOrderVO createOrder(Long userId, OrderCreateRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "订单商品不能为空");
        }
        if (!StringUtils.hasText(request.getReceiverName())
                || !StringUtils.hasText(request.getReceiverPhone())
                || !StringUtils.hasText(request.getReceiverAddress())) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "收货信息不完整");
        }

        Map<Long, Integer> productQtyMap = new LinkedHashMap<>();
        for (OrderCreateRequest.Item item : request.getItems()) {
            if (item.getProductId() == null || item.getQuantity() == null || item.getQuantity() < 1) {
                throw new ServiceException(ResultCode.BAD_REQUEST, "订单商品参数错误");
            }
            productQtyMap.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        List<Product> products = productService.listByIds(productQtyMap.keySet());
        if (products.size() != productQtyMap.size()) {
            throw new ServiceException(ResultCode.NOT_FOUND, "存在无效商品");
        }
        Map<Long, Product> productMap = products.stream().collect(Collectors.toMap(Product::getId, p -> p));

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : productQtyMap.entrySet()) {
            Product product = productMap.get(entry.getKey());
            if (product == null || !Objects.equals(product.getStatus(), 1)) {
                throw new ServiceException(ResultCode.NOT_FOUND, "商品不存在或已下架");
            }
            if (product.getStock() < entry.getValue()) {
                throw new ServiceException(ResultCode.BAD_REQUEST, "库存不足: " + product.getName());
            }
            totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(entry.getValue())));
        }

        Orders order = copyMapper.toOrders(request);
        order.setOrderNo(generateOrderNo(userId));
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus(0);
        save(order);

        List<OrderItem> orderItems = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : productQtyMap.entrySet()) {
            Product product = productMap.get(entry.getKey());
            Integer qty = entry.getValue();

            product.setStock(product.getStock() - qty);
            productService.updateById(product);

            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setProductPrice(product.getPrice());
            item.setQuantity(qty);
            item.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(qty)));
            orderItems.add(item);
        }
        orderItemService.saveBatch(orderItems);
        cartService.removeByUserAndProductIds(userId, new ArrayList<>(productQtyMap.keySet()));

        return new CreateOrderVO(order.getOrderNo(), order.getId());
    }

    @Override
    public List<OrderVO> listOrders(Long userId) {
        List<Orders> orders = lambdaQuery()
                .eq(Orders::getUserId, userId)
                .orderByDesc(Orders::getCreateTime)
                .list();
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> orderIds = orders.stream().map(Orders::getId).toList();
        Map<Long, List<OrderItem>> orderItemMap = orderItemService.lambdaQuery()
                .in(OrderItem::getOrderId, orderIds)
                .list()
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        return orders.stream().map(order -> {
            List<OrderItem> items = orderItemMap.getOrDefault(order.getId(), Collections.emptyList());
            OrderVO vo = copyMapper.toOrderVO(order, items);
            vo.setCreateTime(order.getCreateTime() == null ? null : order.getCreateTime().format(TIME_FORMATTER));
            return vo;
        }).toList();
    }

    @Override
    public void confirmOrder(Long userId, Long orderId) {
        Orders order = getUserOrder(userId, orderId);
        if (!Objects.equals(order.getStatus(), 2)) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "仅已发货订单可确认收货");
        }
        order.setStatus(3);
        updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long userId, Long orderId) {
        Orders order = getUserOrder(userId, orderId);
        if (!Objects.equals(order.getStatus(), 0)) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "仅待支付订单可取消");
        }

        List<OrderItem> items = orderItemService.lambdaQuery()
                .eq(OrderItem::getOrderId, order.getId())
                .list();
        for (OrderItem item : items) {
            Product product = productService.getById(item.getProductId());
            if (product != null) {
                product.setStock(product.getStock() + item.getQuantity());
                productService.updateById(product);
            }
        }

        order.setStatus(4);
        updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long userId, OrderPayRequest request) {
        if (request == null || request.getOrderId() == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "参数错误");
        }
        Orders order = getUserOrder(userId, request.getOrderId());
        if (!Objects.equals(order.getStatus(), 0)) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "订单状态不正确，无法支付");
        }
        
        // 模拟支付成功
        order.setStatus(1);
        // 如果有支付方式字段也可以在这里记录
        updateById(order);
    }

    @Override
    public List<MerchantOrderVO> listMerchantOrders(Long merchantId) {
        // 1. 获取该商家的所有商品
        List<Product> products = productService.lambdaQuery()
                .eq(Product::getMerchantId, merchantId)
                .list();
        if (products.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> productIds = products.stream().map(Product::getId).collect(Collectors.toList());

        // 2. 获取包含这些商品的订单项
        List<OrderItem> orderItems = orderItemService.lambdaQuery()
                .in(OrderItem::getProductId, productIds)
                .list();
        if (orderItems.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 3. 提取唯一的订单ID
        List<Long> orderIds = orderItems.stream()
                .map(OrderItem::getOrderId)
                .distinct()
                .collect(Collectors.toList());

        // 4. 查询订单并映射为VO
        List<Orders> orders = lambdaQuery()
                .in(Orders::getId, orderIds)
                .orderByDesc(Orders::getCreateTime)
                .list();

        return orders.stream().map(order -> {
            MerchantOrderVO vo = copyMapper.toMerchantOrderVO(order);
            vo.setCreateTime(order.getCreateTime() == null ? null : order.getCreateTime().format(TIME_FORMATTER));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deliverOrder(Long merchantId, Long orderId) {
        if (orderId == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "订单ID不能为空");
        }
        
        Orders order = getById(orderId);
        if (order == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!Objects.equals(order.getStatus(), 1)) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "订单当前状态不支持发货");
        }

        // 校验该订单是否包含该商家的商品
        List<OrderItem> items = orderItemService.lambdaQuery()
                .eq(OrderItem::getOrderId, orderId)
                .list();
        if (items.isEmpty()) {
            throw new ServiceException(ResultCode.NOT_FOUND, "订单数据异常");
        }
        
        List<Long> productIds = items.stream().map(OrderItem::getProductId).collect(Collectors.toList());
        long count = productService.lambdaQuery()
                .in(Product::getId, productIds)
                .eq(Product::getMerchantId, merchantId)
                .count();
                
        if (count == 0) {
            throw new ServiceException(ResultCode.FORBIDDEN, "无权操作该订单");
        }

        // 更新为已发货状态
        order.setStatus(2);
        updateById(order);
    }

    private Orders getUserOrder(Long userId, Long orderId) {
        if (orderId == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "订单ID不能为空");
        }
        Orders order = lambdaQuery()
                .eq(Orders::getId, orderId)
                .eq(Orders::getUserId, userId)
                .one();
        if (order == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private String generateOrderNo(Long userId) {
        return "T" + System.currentTimeMillis() + userId + (int) (Math.random() * 1000);
    }
}



