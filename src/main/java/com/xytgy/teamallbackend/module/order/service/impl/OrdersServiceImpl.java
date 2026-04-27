package com.xytgy.teamallbackend.module.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.mapstruct.CopyMapper;
import com.xytgy.teamallbackend.module.order.dto.OrderCreateRequest;
import com.xytgy.teamallbackend.module.order.dto.OrderPayRequest;
import com.xytgy.teamallbackend.module.order.dto.OrderReviewRequest;
import com.xytgy.teamallbackend.module.product.entity.ProductReview;
import com.xytgy.teamallbackend.module.product.repository.ProductReviewMapper;
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
import com.xytgy.teamallbackend.module.order.vo.LogisticsVO;
import com.xytgy.teamallbackend.module.order.vo.MerchantOrderVO;
import com.xytgy.teamallbackend.module.order.vo.OrderItemVO;
import com.xytgy.teamallbackend.module.order.vo.OrderVO;
import com.xytgy.teamallbackend.module.order.vo.OrderStatsVO;
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
    
    @Autowired
    private ProductReviewMapper productReviewMapper;

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
    public OrderVO getOrderDetail(Long userId, Long orderId) {
        Orders order = this.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ServiceException(ResultCode.NOT_FOUND, "订单不存在");
        }
        
        List<OrderItem> items = orderItemService.lambdaQuery()
                .eq(OrderItem::getOrderId, orderId)
                .list();
                
        OrderVO vo = copyMapper.toOrderVO(order, items);
        vo.setCreateTime(order.getCreateTime() == null ? null : order.getCreateTime().format(TIME_FORMATTER));
        return vo;
    }

    @Override
    public List<OrderVO> listOrders(Long userId, Integer status) {
        var wrapper = lambdaQuery()
                .eq(Orders::getUserId, userId);
                
        if (status != null) {
            wrapper.eq(Orders::getStatus, status);
        }
        
        List<Orders> orders = wrapper.orderByDesc(Orders::getCreateTime).list();
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
    public void applyRefund(Long userId, Long orderId) {
        Orders order = getUserOrder(userId, orderId);
        if (!Objects.equals(order.getStatus(), 1)) { // 1为已支付(待发货)
            throw new ServiceException(ResultCode.BAD_REQUEST, "仅已支付待发货的订单可申请退款");
        }
        
        // 修改为退款中状态(6)
        order.setStatus(6);
        updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReview(Long userId, OrderReviewRequest request) {
        if (request == null || request.getOrderId() == null || request.getProductId() == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "参数不完整");
        }
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "评分必须在1-5之间");
        }
        if (!StringUtils.hasText(request.getContent()) || request.getContent().trim().length() < 5) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "评价内容不能少于5个字符");
        }

        Orders order = getUserOrder(userId, request.getOrderId());
        if (!Objects.equals(order.getStatus(), 3)) { // 3为已完成(待评价)
            throw new ServiceException(ResultCode.BAD_REQUEST, "订单未完成或已评价");
        }

        // 保存评价
        ProductReview review = new ProductReview();
        review.setProductId(request.getProductId());
        review.setUserId(userId);
        review.setRating(request.getRating());
        review.setContent(request.getContent().trim());
        // 如果有图片字段，可以转为 JSON 存入，目前表结构暂无 images 字段，可忽略或补充
        productReviewMapper.insert(review);

        // 修改订单状态为已评价(4 或 其他约定值，根据注释：4为已取消/已评价，视具体业务而定，假设已评价保持不变或新状态)
        // 假设需求说改状态为 4，但原设计4是已取消。
        // 为了防冲突，假设评价后订单状态更新为 5（已评价），或者业务逻辑默认已完成的订单通过某个标记区分
        // 这里按你文档里的说法：更改订单状态为 `4` (已评价)
        order.setStatus(4); 
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
                
        // 获取订单与商品明细的映射关系
        Map<Long, List<OrderItem>> orderItemMap = orderItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        return orders.stream().map(order -> {
            List<OrderItem> itemsForOrder = orderItemMap.getOrDefault(order.getId(), Collections.emptyList());
            MerchantOrderVO vo = copyMapper.toMerchantOrderVO(order, itemsForOrder);
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

    @Override
    public OrderStatsVO getOrderStats(Long userId) {
        OrderStatsVO stats = OrderStatsVO.builder()
                .unpaid(0)
                .packing(0)
                .delivering(0)
                .reviewing(0)
                .build();
                
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Orders> queryWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        queryWrapper.select("status", "COUNT(*) as count")
                .eq("user_id", userId)
                .groupBy("status");
        List<Map<String, Object>> result = this.listMaps(queryWrapper);
                
        for (Map<String, Object> map : result) {
            Integer status = ((Number) map.get("status")).intValue();
            Integer count = ((Number) map.get("count")).intValue();
            switch (status) {
                case 0 -> stats.setUnpaid(count);
                case 1 -> stats.setPacking(count);
                case 2 -> stats.setDelivering(count);
                case 4 -> stats.setReviewing(count);
            }
        }
        return stats;
    }

    @Override
    public List<LogisticsVO> getOrderLogistics(Long userId, Long orderId) {
        Orders order = getUserOrder(userId, orderId);
        
        // 只有已发货(2)、已完成(3)的订单才有物流信息
        if (order.getStatus() < 2 && order.getStatus() != 4) {
            throw new ServiceException(ResultCode.NOT_FOUND, "订单暂无物流信息");
        }
        
        List<LogisticsVO> logisticsList = new ArrayList<>();
        
        // 模拟物流轨迹（倒序排列：最新的在最前面）
        // 假设基于支付时间或创建时间往后推演几个小时作为物流时间
        java.time.LocalDateTime updateTime = order.getPayTime() != null 
            ? order.getPayTime() 
            : order.getCreateTime();
            
        if (order.getStatus() == 3 || order.getStatus() == 4) {
            logisticsList.add(LogisticsVO.builder()
                .content("包裹已签收，签收人：本人签收。感谢您使用顺丰速运，期待再次为您服务。")
                .time(updateTime.plusHours(48).format(TIME_FORMATTER))
                .build());
        }
        
        logisticsList.add(LogisticsVO.builder()
            .content("派件中，派件员正在为您派送。派件员电话：13800000000")
            .time(updateTime.plusHours(42).format(TIME_FORMATTER))
            .build());
            
        logisticsList.add(LogisticsVO.builder()
            .content("快件已到达【杭州市西湖区集散中心】")
            .time(updateTime.plusHours(36).format(TIME_FORMATTER))
            .build());
            
        logisticsList.add(LogisticsVO.builder()
            .content("快件已发往【杭州市西湖区集散中心】")
            .time(updateTime.plusHours(24).format(TIME_FORMATTER))
            .build());
            
        logisticsList.add(LogisticsVO.builder()
            .content("顺丰速运 已收取快件")
            .time(updateTime.plusHours(12).format(TIME_FORMATTER))
            .build());
            
        logisticsList.add(LogisticsVO.builder()
            .content("商家已发货，等待快递揽收")
            .time(updateTime.format(TIME_FORMATTER))
            .build());
            
        return logisticsList;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveRefund(Long merchantId, Long orderId) {
        Orders order = getMerchantOrder(merchantId, orderId);
        if (!Objects.equals(order.getStatus(), 6)) { // 6为退款申请中
            throw new ServiceException(ResultCode.BAD_REQUEST, "订单状态不正确，无法同意退款");
        }
        
        // 模拟调用微信/支付宝等支付网关执行原路退回逻辑
        // ...

        // 更新订单状态为已退款(7)
        order.setStatus(7);
        updateById(order);
        
        // 如果需要，可以在这里增加库存恢复逻辑
    }

    @Override
    public void refuseRefund(Long merchantId, Long orderId, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "拒绝原因不能为空");
        }
        Orders order = getMerchantOrder(merchantId, orderId);
        if (!Objects.equals(order.getStatus(), 6)) { // 6为退款申请中
            throw new ServiceException(ResultCode.BAD_REQUEST, "订单状态不正确，无法拒绝退款");
        }

        // 更新订单状态为已拒绝退款(8)，并记录拒绝原因
        order.setStatus(8);
        order.setRefusalReason(reason);
        updateById(order);
    }

    private Orders getMerchantOrder(Long merchantId, Long orderId) {
        if (orderId == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "订单ID不能为空");
        }
        Orders order = getById(orderId);
        if (order == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "订单不存在");
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
        
        return order;
    }
}



