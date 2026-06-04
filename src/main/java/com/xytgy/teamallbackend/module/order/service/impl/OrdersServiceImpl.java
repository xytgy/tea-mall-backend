package com.xytgy.teamallbackend.module.order.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.mapstruct.CopyMapper;
import com.xytgy.teamallbackend.config.datasource.ReadOnly;
import com.xytgy.teamallbackend.module.order.dto.OrderCreateRequest;
import com.xytgy.teamallbackend.module.order.dto.OrderPayRequest;
import com.xytgy.teamallbackend.module.order.dto.OrderReviewRequest;
import com.xytgy.teamallbackend.module.product.entity.ProductReview;
import com.xytgy.teamallbackend.module.product.repository.ProductReviewMapper;
import com.xytgy.teamallbackend.module.order.entity.OrderItem;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.order.entity.PaymentRecord;
import com.xytgy.teamallbackend.module.product.entity.Product;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.order.repository.OrdersMapper;
import com.xytgy.teamallbackend.module.cart.service.CartService;
import com.xytgy.teamallbackend.module.order.service.OrderItemService;
import com.xytgy.teamallbackend.module.order.service.OrdersService;
import com.xytgy.teamallbackend.module.order.service.PaymentRecordService;
import com.xytgy.teamallbackend.module.product.service.ProductService;
import com.xytgy.teamallbackend.module.order.vo.CreateOrderVO;
import com.xytgy.teamallbackend.module.order.vo.LogisticsVO;
import com.xytgy.teamallbackend.module.order.vo.MerchantOrderVO;
import com.xytgy.teamallbackend.module.order.vo.OrderVO;
import com.xytgy.teamallbackend.module.order.vo.OrderStatsVO;
import com.xytgy.teamallbackend.config.mq.MqConstants;
import com.xytgy.teamallbackend.config.mq.MqProducer;
import com.xytgy.teamallbackend.utils.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
/**
* @author xytgy
* @description 针对表【orders】的数据库操作Service实现
* @createDate 2026-04-15 08:01:03
*/
@Service
@RequiredArgsConstructor
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper, Orders>
    implements OrdersService{

    private final ProductService productService;
    private final OrderItemService orderItemService;
    private final CartService cartService;
    
    private final CopyMapper copyMapper;
    
    private final ProductReviewMapper productReviewMapper;
    
    private final PaymentRecordService paymentRecordService;
    private final ObjectProvider<AlipayClient> alipayClientProvider;

    // S6809: 自注入，通过代理调用 @Transactional 方法，避免 this 调用导致事务失效
    private final ObjectProvider<OrdersService> selfProvider;
    private final DistributedLock distributedLock;
    private final MqProducer mqProducer;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AlipayClient getAlipayClient() {
        AlipayClient client = alipayClientProvider.getIfAvailable();
        if (client == null) {
            throw new ServiceException(ResultCode.ERROR, "支付宝未配置，无法执行该操作");
        }
        return client;
    }

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

        //批量查询，提高性能
        List<Product> products = productService.listByIds(productQtyMap.keySet());
        if (products.size() != productQtyMap.size()) {
            throw new ServiceException(ResultCode.NOT_FOUND, "存在无效商品");
        }
        //空间换时间优化
        Map<Long, Product> productMap = products.stream().collect(Collectors.toMap(Product::getId, p -> p));

        //避免N+1 性能问题
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

            // 乐观锁扣减库存：WHERE stock >= qty，防止超卖
            boolean stockUpdated = productService.lambdaUpdate()
                    .eq(Product::getId, product.getId())
                    .ge(Product::getStock, qty)
                    .setSql("stock = stock - " + qty)
                    .update();
            if (!stockUpdated) {
                throw new ServiceException(ResultCode.BAD_REQUEST, "库存不足: " + product.getName());
            }

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

        // 下单成功后发送 30 分钟延迟消息，超时未支付自动取消
        Map<String, Object> timeoutMsg = Map.of("orderId", order.getId(), "userId", userId);
        mqProducer.sendDelay(MqConstants.TOPIC_ORDER_TIMEOUT, MqConstants.TAG_TIMEOUT_CANCEL,
                String.valueOf(order.getId()), timeoutMsg, MqConstants.DELAY_LEVEL_30_MINUTES);

        return new CreateOrderVO(order.getOrderNo(), order.getId());
    }

    @ReadOnly
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

    @ReadOnly
    @Override
    public PageResult<OrderVO> listOrders(Long userId, Integer status, int page, int pageSize) {
        // S6213: var 是 Java 10+ 保留标识符，不能作为变量名
        LambdaQueryChainWrapper<Orders> wrapper = lambdaQuery()
                .eq(Orders::getUserId, userId);

        if (status != null) {
            wrapper.eq(Orders::getStatus, status);
        }

        Page<Orders> pageResult = wrapper.orderByDesc(Orders::getCreateTime).page(new Page<>(page, pageSize));
        if (pageResult.getRecords().isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0, page, pageSize);
        }

        List<Long> orderIds = pageResult.getRecords().stream().map(Orders::getId).toList();
        Map<Long, List<OrderItem>> orderItemMap = orderItemService.lambdaQuery()
                .in(OrderItem::getOrderId, orderIds)
                .list()
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        List<OrderVO> voList = pageResult.getRecords().stream().map(order -> {
            List<OrderItem> items = orderItemMap.getOrDefault(order.getId(), Collections.emptyList());
            OrderVO vo = copyMapper.toOrderVO(order, items);
            vo.setCreateTime(order.getCreateTime() == null ? null : order.getCreateTime().format(TIME_FORMATTER));
            return vo;
        }).toList();

        return new PageResult<>(voList, pageResult.getTotal(), page, pageSize);
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
    public void cancelOrder(Long userId, Long orderId) {
        //先做检验
        Orders order = getUserOrder(userId, orderId);
        if (!Objects.equals(order.getStatus(), 0)) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "仅待支付订单可取消");
        }

        // 远程调用放在事务外，避免占用数据库连接
        PaymentRecord payingRecord = paymentRecordService.getLastPayingRecord(order.getId());
        if (payingRecord != null && Objects.equals(payingRecord.getStatus(), PaymentRecord.STATUS_PAYING)) {
            closeAlipayTrade(payingRecord);
        }

        // S6809: 通过代理调用，确保 @Transactional 生效
        selfProvider.getObject().doCancelOrderInTransaction(order);
    }

    // S3776: 拆分高认知复杂度方法，消除 6 层嵌套
    private void closeAlipayTrade(PaymentRecord payingRecord) {
        try {
            AlipayTradeQueryResponse queryResponse = queryAlipayTradeStatus(payingRecord.getOutTradeNo());
            if (!queryResponse.isSuccess()) {
                markRecordClosed(payingRecord);
                return;
            }
            handleTradeStatusForClose(payingRecord, queryResponse.getTradeStatus());
        } catch (AlipayApiException e) {
            throw new ServiceException(ResultCode.ERROR, "支付宝关单异常: " + e.getMessage());
        }
    }

    private AlipayTradeQueryResponse queryAlipayTradeStatus(String outTradeNo) throws AlipayApiException {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(outTradeNo);
        request.setBizModel(model);
        return getAlipayClient().execute(request);
    }

    private void handleTradeStatusForClose(PaymentRecord record, String tradeStatus) throws AlipayApiException {
        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "该订单已完成支付，无法取消；如需撤销请在订单中发起退款。");
        }
        if ("TRADE_CLOSED".equals(tradeStatus)) {
            markRecordClosed(record);
            return;
        }
        closeAlipayTradeAndRetry(record);
    }

    private void closeAlipayTradeAndRetry(PaymentRecord record) throws AlipayApiException {
        AlipayTradeCloseRequest closeRequest = new AlipayTradeCloseRequest();
        AlipayTradeCloseModel closeModel = new AlipayTradeCloseModel();
        closeModel.setOutTradeNo(record.getOutTradeNo());
        closeRequest.setBizModel(closeModel);

        AlipayTradeCloseResponse closeResponse = getAlipayClient().execute(closeRequest);
        if (closeResponse.isSuccess()) {
            markRecordClosed(record);
            return;
        }
        AlipayTradeQueryResponse requery = queryAlipayTradeStatus(record.getOutTradeNo());
        if (requery.isSuccess()) {
            handleTradeStatusForClose(record, requery.getTradeStatus());
            return;
        }
        throw new ServiceException(ResultCode.ERROR, "支付宝关单失败: " + closeResponse.getSubMsg());
    }

    private void markRecordClosed(PaymentRecord record) {
        record.setStatus(PaymentRecord.STATUS_CLOSED);
        paymentRecordService.updateById(record);
    }

    // S2229: 显式声明传播行为，避免嵌套事务配置冲突
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void doCancelOrderInTransaction(Orders order) {
        List<OrderItem> items = orderItemService.lambdaQuery()
                .eq(OrderItem::getOrderId, order.getId())
                .list();
        if (!items.isEmpty()) {
            Map<Long, Integer> restoreMap = items.stream()
                    .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity, Integer::sum));
            for (Map.Entry<Long, Integer> entry : restoreMap.entrySet()) {
                productService.lambdaUpdate()
                        .eq(Product::getId, entry.getKey())
                        .setSql("stock = stock + " + entry.getValue())
                        .update();
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

        String lockKey = "order:pay:" + request.getOrderId();
        if (!distributedLock.tryLock(lockKey)) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "请勿重复提交");
        }
        try {
            Orders order = getUserOrder(userId, request.getOrderId());
            if (!Objects.equals(order.getStatus(), 0)) {
                throw new ServiceException(ResultCode.BAD_REQUEST, "订单状态不正确，无法支付");
            }
            order.setStatus(1);
            updateById(order);
        } finally {
            distributedLock.unlock(lockKey);
        }
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
    @ReadOnly
    @Override
    public PageResult<MerchantOrderVO> listMerchantOrders(Long merchantId, int page, int pageSize) {
        // 1. 获取该商家的所有商品
        List<Product> products = productService.lambdaQuery()
                .eq(Product::getMerchantId, merchantId)
                .list();
        if (products.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0, page, pageSize);
        }
        List<Long> productIds = products.stream().map(Product::getId).toList();

        // 2. 获取包含这些商品的订单项
        List<OrderItem> orderItems = orderItemService.lambdaQuery()
                .in(OrderItem::getProductId, productIds)
                .list();
        if (orderItems.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0, page, pageSize);
        }

        // 3. 提取唯一的订单ID
        List<Long> orderIds = orderItems.stream()
                .map(OrderItem::getOrderId)
                .distinct()
                .toList();

        // 4. 分页查询订单
        Page<Orders> pageResult = lambdaQuery()
                .in(Orders::getId, orderIds)
                .orderByDesc(Orders::getCreateTime)
                .page(new Page<>(page, pageSize));

        // 获取订单与商品明细的映射关系
        Map<Long, List<OrderItem>> orderItemMap = orderItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        List<MerchantOrderVO> voList = pageResult.getRecords().stream().map(order -> {
            List<OrderItem> itemsForOrder = orderItemMap.getOrDefault(order.getId(), Collections.emptyList());
            MerchantOrderVO vo = copyMapper.toMerchantOrderVO(order, itemsForOrder);
            vo.setCreateTime(order.getCreateTime() == null ? null : order.getCreateTime().format(TIME_FORMATTER));
            // 收货人手机号脱敏，防止商家批量获取买家手机号
            if (vo.getReceiverPhone() != null && vo.getReceiverPhone().length() > 7) {
                vo.setReceiverPhone(vo.getReceiverPhone().substring(0, 3) + "****" + vo.getReceiverPhone().substring(7));
            }
            return vo;
        }).toList();

        return new PageResult<>(voList, pageResult.getTotal(), page, pageSize);
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
        
        List<Long> productIds = items.stream().map(OrderItem::getProductId).toList();
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

    @ReadOnly
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
            // S131: switch 必须包含 default 分支
            switch (status) {
                case 0 -> stats.setUnpaid(count);
                case 1 -> stats.setPacking(count);
                case 2 -> stats.setDelivering(count);
                case 3 -> stats.setReviewing(count);
                default -> { }
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

    // S2140: 使用 AtomicInteger 替代 Math.random()，线程安全且可排序
    private static final java.util.concurrent.atomic.AtomicInteger ORDER_SEQ = new java.util.concurrent.atomic.AtomicInteger(0);

    private String generateOrderNo(Long userId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String userPart = String.format("%04d", userId % 10000);
        String seqPart = String.format("%04d", ORDER_SEQ.incrementAndGet() % 10000);
        return "T" + timestamp + userPart + seqPart;
    }

    @Override
    public void approveRefund(Long merchantId, Long orderId) {
        Orders order = getMerchantOrder(merchantId, orderId);
        if (!Objects.equals(order.getStatus(), 6)) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "订单状态不正确，无法同意退款");
        }

        PaymentRecord record = null;
        if (order.getPaymentId() != null) {
            record = paymentRecordService.getById(order.getPaymentId());
        }
        if (record == null) {
            record = paymentRecordService.getLastPaidRecord(order.getId());
        }
        if (record == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "无有效支付记录，无法退款");
        }

        if (!Objects.equals(record.getStatus(), PaymentRecord.STATUS_PAID) && !Objects.equals(record.getStatus(), PaymentRecord.STATUS_REFUNDED)) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "支付记录状态异常，无法退款");
        }

        BigDecimal refundAmount = record.getTotalAmount();
        if (refundAmount == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "支付金额异常，无法退款");
        }

        // 远程调用放在事务外
        if (!Objects.equals(record.getStatus(), PaymentRecord.STATUS_REFUNDED)) {
            String outRequestNo = "refund_" + order.getId();
            AlipayTradeRefundRequest refundRequest = new AlipayTradeRefundRequest();
            AlipayTradeRefundModel refundModel = new AlipayTradeRefundModel();
            refundModel.setOutTradeNo(record.getOutTradeNo());
            refundModel.setRefundAmount(refundAmount.toString());
            refundModel.setOutRequestNo(outRequestNo);
            refundRequest.setBizModel(refundModel);
            try {
                AlipayTradeRefundResponse refundResponse = getAlipayClient().execute(refundRequest);
                if (!refundResponse.isSuccess()) {
                    throw new ServiceException(ResultCode.ERROR, "支付宝退款失败: " + refundResponse.getSubMsg());
                }
                record.setStatus(PaymentRecord.STATUS_REFUNDED);
                paymentRecordService.updateById(record);
            } catch (AlipayApiException e) {
                throw new ServiceException(ResultCode.ERROR, "支付宝退款异常: " + e.getMessage());
            }
        }

        // 事务内只做数据库操作
        doApproveRefundInTransaction(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void doApproveRefundInTransaction(Orders order) {
        boolean updated = lambdaUpdate()
                .set(Orders::getStatus, 7)
                .set(Orders::getRefusalReason, null)
                .eq(Orders::getId, order.getId())
                .eq(Orders::getStatus, 6)
                .update();
                
        if (!updated) {
            Orders latest = getById(order.getId());
            if (latest != null && Objects.equals(latest.getStatus(), 7)) {
                return;
            }
            throw new ServiceException(ResultCode.BAD_REQUEST, "操作失败，订单状态已发生改变");
        }
        
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

        // 并发控制：使用带状态条件的 update
        boolean updated = lambdaUpdate()
                .set(Orders::getStatus, 8)
                .set(Orders::getRefusalReason, reason)
                .eq(Orders::getId, order.getId())
                .eq(Orders::getStatus, 6)
                .update();
                
        if (!updated) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "操作失败，订单状态已发生改变");
        }
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
        
        List<Long> productIds = items.stream().map(OrderItem::getProductId).toList();
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
