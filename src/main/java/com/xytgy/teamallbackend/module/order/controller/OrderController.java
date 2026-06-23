package com.xytgy.teamallbackend.module.order.controller;

import com.xytgy.teamallbackend.common.BaseController;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.module.order.dto.OrderCreateRequest;
import com.xytgy.teamallbackend.module.order.dto.OrderPayRequest;
import com.xytgy.teamallbackend.module.order.dto.OrderRefundRefuseRequest;
import com.xytgy.teamallbackend.module.order.dto.OrderReviewRequest;
import com.xytgy.teamallbackend.module.order.service.OrdersService;
import com.xytgy.teamallbackend.module.order.vo.CreateOrderVO;
import com.xytgy.teamallbackend.module.order.vo.LogisticsVO;
import com.xytgy.teamallbackend.module.order.vo.MerchantOrderVO;
import com.xytgy.teamallbackend.module.order.vo.OrderVO;
import com.xytgy.teamallbackend.module.order.vo.OrderStatsVO;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 订单模块 HTTP 入口。
 * <p>
 * 这个 Controller 只做三件事：
 * 1. 从登录态中取出当前用户/商家身份；
 * 2. 接收并校验请求参数；
 * 3. 把请求转交给 {@link OrdersService} 处理。
 * <p>
 * 业务规则本身基本都在 service 层，这里更适合作为“订单能力地图”来阅读。
 * 可以先按买家接口看一遍，再按商家接口看一遍。
 *
 *
 * 排查到的 Bug： swagger.v3.oas.annotations.parameters.RequestBody 的显式导入（第 28 行）覆盖了 org.springframework.web.bind.annotation.* 通配导入中的 Spring
 *   @RequestBody，导致请求体未被反序列化，所有字段验证都报"不能为空"。
 */
@RestController
@RequestMapping("/api/order")
@Tag(name = "订单")
@SecurityRequirement(name = "BearerAuth")
@Validated
@RequiredArgsConstructor
public class OrderController extends BaseController {

    private final OrdersService ordersService;

    @Value("${mock-pay.enabled:false}")
    private boolean mockPayEnabled;

    /**
     * 买家创建普通订单。
     * <p>
     * 主链路会进入 OrdersServiceImpl#createOrder：
     * 校验商品 -> 计算总价 -> 落订单主表/明细表 -> 扣库存 -> 发送超时取消 MQ。
     */
    @PostMapping("/create")
    @Operation(summary = "创建订单")
    @ApiResponse(
            responseCode = "200",
            description = "成功",
            content = @Content(examples = @ExampleObject(value = "{\"code\":200,\"message\":\"成功\",\"data\":{\"orderNo\":\"T17133333333331001\",\"orderId\":101}}"))
    )
    @ApiResponse(responseCode = "400", description = "参数错误/库存不足")
    @ApiResponse(responseCode = "401", description = "未登录")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = OrderCreateRequest.class),
                    examples = @ExampleObject(value = "{\"items\":[{\"productId\":1,\"quantity\":2}],\"receiverName\":\"张三\",\"receiverPhone\":\"13800138000\",\"receiverAddress\":\"上海市浦东新区xx路88号\"}")
            )
    )
    public Result<CreateOrderVO> create(@Valid @RequestBody OrderCreateRequest request) {
        Long userId = currentUserId();
        return Result.success(ordersService.createOrder(userId, request));
    }

    /**
     * 本地/开发环境使用的模拟支付入口。
     * 真实支付宝支付主入口在 PaymentController，这里只是受开关保护的辅助接口。
     */
    @PostMapping("/pay")
    @Operation(summary = "支付订单 (模拟支付)")
    public Result<Void> pay(@Valid @RequestBody OrderPayRequest request) {
        if (!mockPayEnabled) {
            throw new com.xytgy.teamallbackend.exception.ServiceException(
                    com.xytgy.teamallbackend.common.ResultCode.NOT_FOUND, "接口不存在");
        }
        Long userId = currentUserId();
        ordersService.payOrder(userId, request);
        return Result.success(null);
    }

    /**
     * 买家查看单个订单详情。
     * 会校验订单归属，避免用户读取别人的订单。
     */
    @GetMapping("/detail")
    @Operation(summary = "获取订单详情")
    public Result<OrderVO> detail(@RequestParam("orderId") Long id) {
        Long userId = currentUserId();
        return Result.success(ordersService.getOrderDetail(userId, id));
    }

    /**
     * 买家分页查看自己的订单列表，可按状态筛选。
     */
    @GetMapping("/list")
    @Operation(summary = "订单列表")
    @ApiResponse(
            responseCode = "200",
            description = "成功",
            content = @Content(examples = @ExampleObject(value = "{\"code\":200,\"message\":\"成功\",\"data\":[{\"id\":101,\"orderNo\":\"T17133333333331001\",\"totalAmount\":39.00,\"status\":0,\"receiverName\":\"张三\",\"receiverPhone\":\"13800138000\",\"receiverAddress\":\"上海市浦东新区xx路88号\",\"createTime\":\"2026-04-17 16:30:00\",\"items\":[{\"productName\":\"茉莉绿茶\",\"productPrice\":12.00,\"quantity\":2}]}]}"))
    )
    @ApiResponse(responseCode = "401", description = "未登录")
    public Result<PageResult<OrderVO>> list(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Long userId = currentUserId();
        return Result.success(ordersService.listOrders(userId, status, page, pageSize));
    }

    /**
     * 买家首页常用的订单数量统计，如待支付、待收货等。
     */
    @GetMapping("/stats")
    @Operation(summary = "获取订单数量统计")
    public Result<OrderStatsVO> stats() {
        Long userId = currentUserId();
        return Result.success(ordersService.getOrderStats(userId));
    }

    /**
     * 买家确认收货。
     * 正常状态流转一般是：待支付 -> 已支付 -> 已发货 -> 已完成。
     */
    @PostMapping("/confirm/{orderId}")
    @Operation(summary = "确认收货")
    @ApiResponse(
            responseCode = "200",
            description = "成功",
            content = @Content(examples = @ExampleObject(value = "{\"code\":200,\"message\":\"成功\",\"data\":null}"))
    )
    @ApiResponse(responseCode = "400", description = "状态非法流转")
    @ApiResponse(responseCode = "404", description = "订单不存在")
    @ApiResponse(responseCode = "401", description = "未登录")
    public Result<Void> confirm(@PathVariable Long orderId) {
        Long userId = currentUserId();
        ordersService.confirmOrder(userId, orderId);
        return Result.success(null);
    }

    /**
     * 买家主动取消订单。
     * 一般只允许取消还未进入后续履约阶段的订单。
     */
    @PostMapping("/cancel/{orderId}")
    @Operation(summary = "取消订单")
    public Result<Void> cancel(@PathVariable Long orderId) {
        Long userId = currentUserId();
        ordersService.cancelOrder(userId, orderId);
        return Result.success(null);
    }

    /**
     * 买家发起退款申请，后续由商家侧处理同意/拒绝。
     */
    @PostMapping("/refund/{orderId}")
    @Operation(summary = "申请退款")
    public Result<Void> applyRefund(@PathVariable Long orderId) {
        Long userId = currentUserId();
        ordersService.applyRefund(userId, orderId);
        return Result.success("退款申请已提交", null);
    }

    /**
     * 买家对已完成订单提交评价。
     */
    @PostMapping("/review")
    @Operation(summary = "提交评价")
    public Result<Void> submitReview(@Valid @RequestBody OrderReviewRequest request) {
        Long userId = currentUserId();
        ordersService.submitReview(userId, request);
        return Result.success("评价发表成功", null);
    }

    /**
     * 买家查看订单物流轨迹。
     */
    @GetMapping("/logistics")
    @Operation(summary = "获取订单物流信息")
    public Result<List<LogisticsVO>> logistics(@RequestParam("orderId") Long orderId) {
        Long userId = currentUserId();
        return Result.success(ordersService.getOrderLogistics(userId, orderId));
    }

    /**
     * 商家分页查看“自己店铺”的订单。
     * 这里取的是 shopId，不是普通 userId，这是商家链路阅读时最容易忽略的点。
     */
    @PreAuthorize("hasRole('MERCHANT')")
    @GetMapping("/merchant/list")
    @Operation(summary = "商家获取自己的订单列表")
    public Result<PageResult<MerchantOrderVO>> merchantList(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Long shopId = currentShopId();
        return Result.success(ordersService.listMerchantOrders(shopId, page, pageSize));
    }

    /**
     * 商家发货，推动订单从“已支付”进入“已发货”。
     */
    @PreAuthorize("hasRole('MERCHANT')")
    @PostMapping("/merchant/deliver/{orderId}")
    @Operation(summary = "商家对订单进行发货")
    public Result<Void> deliver(@PathVariable Long orderId) {
        Long shopId = currentShopId();
        ordersService.deliverOrder(shopId, orderId);
        return Result.success(null);
    }

    /**
     * 商家同意退款。
     */
    @PreAuthorize("hasRole('MERCHANT')")
    @PostMapping("/merchant/refund/{orderId}/approve")
    @Operation(summary = "商家同意退款")
    public Result<Void> approveRefund(@PathVariable Long orderId) {
        Long shopId = currentShopId();
        ordersService.approveRefund(shopId, orderId);
        return Result.success("操作成功", null);
    }

    /**
     * 商家拒绝退款，并记录拒绝原因。
     */
    @PreAuthorize("hasRole('MERCHANT')")
    @PostMapping("/merchant/refund/{orderId}/refuse")
    @Operation(summary = "商家拒绝退款")
    public Result<Void> refuseRefund(@PathVariable Long orderId, @Valid @RequestBody OrderRefundRefuseRequest request) {
        Long shopId = currentShopId();
        ordersService.refuseRefund(shopId, orderId, request.getReason());
        return Result.success("操作成功", null);
    }
}
