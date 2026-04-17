package com.xytgy.teamallbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.dto.OrderCreateRequest;
import com.xytgy.teamallbackend.entity.OrderItem;
import com.xytgy.teamallbackend.entity.Orders;
import com.xytgy.teamallbackend.entity.Product;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.service.CartService;
import com.xytgy.teamallbackend.service.OrderItemService;
import com.xytgy.teamallbackend.service.OrdersService;
import com.xytgy.teamallbackend.mapper.OrdersMapper;
import com.xytgy.teamallbackend.service.ProductService;
import com.xytgy.teamallbackend.vo.CreateOrderVO;
import com.xytgy.teamallbackend.vo.OrderItemVO;
import com.xytgy.teamallbackend.vo.OrderVO;
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

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateOrderVO createOrder(Long userId, OrderCreateRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new ServiceException(400, "订单商品不能为空");
        }
        if (!StringUtils.hasText(request.getReceiverName())
                || !StringUtils.hasText(request.getReceiverPhone())
                || !StringUtils.hasText(request.getReceiverAddress())) {
            throw new ServiceException(400, "收货信息不完整");
        }

        Map<Long, Integer> productQtyMap = new LinkedHashMap<>();
        for (OrderCreateRequest.Item item : request.getItems()) {
            if (item.getProductId() == null || item.getQuantity() == null || item.getQuantity() < 1) {
                throw new ServiceException(400, "订单商品参数错误");
            }
            productQtyMap.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        List<Product> products = productService.listByIds(productQtyMap.keySet());
        if (products.size() != productQtyMap.size()) {
            throw new ServiceException(404, "存在无效商品");
        }
        Map<Long, Product> productMap = products.stream().collect(Collectors.toMap(Product::getId, p -> p));

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : productQtyMap.entrySet()) {
            Product product = productMap.get(entry.getKey());
            if (product == null || !Objects.equals(product.getStatus(), 1)) {
                throw new ServiceException(404, "商品不存在或已下架");
            }
            if (product.getStock() < entry.getValue()) {
                throw new ServiceException(400, "库存不足: " + product.getName());
            }
            totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(entry.getValue())));
        }

        Orders order = new Orders();
        order.setOrder_no(generateOrderNo(userId));
        order.setUser_id(userId);
        order.setTotal_amount(totalAmount);
        order.setStatus(0);
        order.setReceiver_name(request.getReceiverName());
        order.setReceiver_phone(request.getReceiverPhone());
        order.setReceiver_address(request.getReceiverAddress());
        save(order);

        List<OrderItem> orderItems = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : productQtyMap.entrySet()) {
            Product product = productMap.get(entry.getKey());
            Integer qty = entry.getValue();

            product.setStock(product.getStock() - qty);
            productService.updateById(product);

            OrderItem item = new OrderItem();
            item.setOrder_id(order.getId());
            item.setProduct_id(product.getId());
            item.setProduct_name(product.getName());
            item.setProduct_price(product.getPrice());
            item.setQuantity(qty);
            item.setTotal_amount(product.getPrice().multiply(BigDecimal.valueOf(qty)));
            orderItems.add(item);
        }
        orderItemService.saveBatch(orderItems);
        cartService.removeByUserAndProductIds(userId, new ArrayList<>(productQtyMap.keySet()));

        return new CreateOrderVO(order.getOrder_no(), order.getId());
    }

    @Override
    public List<OrderVO> listOrders(Long userId) {
        List<Orders> orders = lambdaQuery()
                .eq(Orders::getUser_id, userId)
                .orderByDesc(Orders::getCreate_time)
                .list();
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> orderIds = orders.stream().map(Orders::getId).toList();
        Map<Long, List<OrderItem>> orderItemMap = orderItemService.lambdaQuery()
                .in(OrderItem::getOrder_id, orderIds)
                .list()
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrder_id));

        return orders.stream().map(order -> OrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrder_no())
                .totalAmount(order.getTotal_amount())
                .status(order.getStatus())
                .receiverName(order.getReceiver_name())
                .receiverPhone(order.getReceiver_phone())
                .receiverAddress(order.getReceiver_address())
                .createTime(order.getCreate_time() == null ? null : order.getCreate_time().format(TIME_FORMATTER))
                .items(orderItemMap.getOrDefault(order.getId(), Collections.emptyList())
                        .stream()
                        .map(item -> OrderItemVO.builder()
                                .productName(item.getProduct_name())
                                .productPrice(item.getProduct_price())
                                .quantity(item.getQuantity())
                                .build())
                        .toList())
                .build()).toList();
    }

    @Override
    public void confirmOrder(Long userId, Long orderId) {
        Orders order = getUserOrder(userId, orderId);
        if (!Objects.equals(order.getStatus(), 2)) {
            throw new ServiceException(400, "仅已发货订单可确认收货");
        }
        order.setStatus(3);
        updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long userId, Long orderId) {
        Orders order = getUserOrder(userId, orderId);
        if (!Objects.equals(order.getStatus(), 0)) {
            throw new ServiceException(400, "仅待支付订单可取消");
        }

        List<OrderItem> items = orderItemService.lambdaQuery()
                .eq(OrderItem::getOrder_id, order.getId())
                .list();
        for (OrderItem item : items) {
            Product product = productService.getById(item.getProduct_id());
            if (product != null) {
                product.setStock(product.getStock() + item.getQuantity());
                productService.updateById(product);
            }
        }

        order.setStatus(4);
        updateById(order);
    }

    private Orders getUserOrder(Long userId, Long orderId) {
        if (orderId == null) {
            throw new ServiceException(400, "订单ID不能为空");
        }
        Orders order = lambdaQuery()
                .eq(Orders::getId, orderId)
                .eq(Orders::getUser_id, userId)
                .one();
        if (order == null) {
            throw new ServiceException(404, "订单不存在");
        }
        return order;
    }

    private String generateOrderNo(Long userId) {
        return "T" + System.currentTimeMillis() + userId + (int) (Math.random() * 1000);
    }
}




