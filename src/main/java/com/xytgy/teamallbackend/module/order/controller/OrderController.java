package com.xytgy.teamallbackend.module.order.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.module.order.dto.OrderCreateRequest;
import com.xytgy.teamallbackend.module.order.dto.OrderPayRequest;
import com.xytgy.teamallbackend.module.order.dto.OrderRefundRefuseRequest;
import com.xytgy.teamallbackend.module.order.dto.OrderReviewRequest;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.order.service.OrdersService;
import com.xytgy.teamallbackend.module.order.vo.CreateOrderVO;
import com.xytgy.teamallbackend.module.order.vo.LogisticsVO;
import com.xytgy.teamallbackend.module.order.vo.MerchantOrderVO;
import com.xytgy.teamallbackend.module.order.vo.OrderVO;
import com.xytgy.teamallbackend.module.order.vo.OrderStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
@Tag(name = "订单")
@SecurityRequirement(name = "BearerAuth")
@RequiredArgsConstructor
public class OrderController {

    private final OrdersService ordersService;

    @PostMapping("/create")
    @Operation(summary = "创建订单")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "成功",
                    content = @Content(examples = @ExampleObject(value = "{\"code\":200,\"message\":\"成功\",\"data\":{\"orderNo\":\"T17133333333331001\",\"orderId\":101}}"))
            ),
            @ApiResponse(responseCode = "400", description = "参数错误/库存不足"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = OrderCreateRequest.class),
                    examples = @ExampleObject(value = "{\"items\":[{\"productId\":1,\"quantity\":2}],\"receiverName\":\"张三\",\"receiverPhone\":\"13800138000\",\"receiverAddress\":\"上海市浦东新区xx路88号\"}")
            )
    )
    public Result<CreateOrderVO> create(@RequestBody OrderCreateRequest request) {
        Long userId = currentUserId();
        return Result.success(ordersService.createOrder(userId, request));
    }

    @PostMapping("/pay")
    @Operation(summary = "支付订单 (模拟支付)")
    public Result<Void> pay(@RequestBody OrderPayRequest request) {
        Long userId = currentUserId();
        ordersService.payOrder(userId, request);
        return Result.success(null);
    }

    @GetMapping("/detail")
    @Operation(summary = "获取订单详情")
    public Result<OrderVO> detail(@RequestParam("orderId") Long orderId) {
        Long userId = currentUserId();
        return Result.success(ordersService.getOrderDetail(userId, orderId));
    }

    @GetMapping("/list")
    @Operation(summary = "订单列表")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "成功",
                    content = @Content(examples = @ExampleObject(value = "{\"code\":200,\"message\":\"成功\",\"data\":[{\"id\":101,\"orderNo\":\"T17133333333331001\",\"totalAmount\":39.00,\"status\":0,\"receiverName\":\"张三\",\"receiverPhone\":\"13800138000\",\"receiverAddress\":\"上海市浦东新区xx路88号\",\"createTime\":\"2026-04-17 16:30:00\",\"items\":[{\"productName\":\"茉莉绿茶\",\"productPrice\":12.00,\"quantity\":2}]}]}"))
            ),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public Result<List<OrderVO>> list(@RequestParam(value = "status", required = false) Integer status) {
        Long userId = currentUserId();
        return Result.success(ordersService.listOrders(userId, status));
    }

    @GetMapping("/stats")
    @Operation(summary = "获取订单数量统计")
    public Result<OrderStatsVO> stats() {
        Long userId = currentUserId();
        return Result.success(ordersService.getOrderStats(userId));
    }

    @PostMapping("/confirm/{orderId}")
    @Operation(summary = "确认收货")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "成功",
                    content = @Content(examples = @ExampleObject(value = "{\"code\":200,\"message\":\"成功\",\"data\":null}"))
            ),
            @ApiResponse(responseCode = "400", description = "状态非法流转"),
            @ApiResponse(responseCode = "404", description = "订单不存在"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public Result<Void> confirm(@PathVariable Long orderId) {
        Long userId = currentUserId();
        ordersService.confirmOrder(userId, orderId);
        return Result.success(null);
    }

    @PostMapping("/cancel/{orderId}")
    @Operation(summary = "取消订单")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "成功",
                    content = @Content(examples = @ExampleObject(value = "{\"code\":200,\"message\":\"成功\",\"data\":null}"))
            ),
            @ApiResponse(responseCode = "400", description = "状态非法流转"),
            @ApiResponse(responseCode = "404", description = "订单不存在"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public Result<Void> cancel(@PathVariable Long orderId) {
        Long userId = currentUserId();
        ordersService.cancelOrder(userId, orderId);
        return Result.success(null);
    }

    @PostMapping("/refund/{orderId}")
    @Operation(summary = "申请退款")
    public Result<Void> applyRefund(@PathVariable Long orderId) {
        Long userId = currentUserId();
        ordersService.applyRefund(userId, orderId);
        return Result.success("退款申请已提交", null);
    }

    @PostMapping("/review")
    @Operation(summary = "提交评价")
    public Result<Void> submitReview(@RequestBody OrderReviewRequest request) {
        Long userId = currentUserId();
        ordersService.submitReview(userId, request);
        return Result.success("评价发表成功", null);
    }

    @GetMapping("/logistics")
    @Operation(summary = "获取订单物流信息")
    public Result<List<LogisticsVO>> logistics(@RequestParam("orderId") Long orderId) {
        Long userId = currentUserId();
        return Result.success(ordersService.getOrderLogistics(userId, orderId));
    }

    @GetMapping("/merchant/list")
    @Operation(summary = "商家获取自己的订单列表")
    public Result<List<MerchantOrderVO>> merchantList() {
        requireRole(1);
        Long shopId = currentShopId();
        return Result.success(ordersService.listMerchantOrders(shopId));
    }

    @PostMapping("/merchant/deliver/{orderId}")
    @Operation(summary = "商家对订单进行发货")
    public Result<Void> deliver(@PathVariable Long orderId) {
        requireRole(1);
        Long shopId = currentShopId();
        ordersService.deliverOrder(shopId, orderId);
        return Result.success(null);
    }

    @PostMapping("/merchant/refund/{orderId}/approve")
    @Operation(summary = "商家同意退款")
    public Result<Void> approveRefund(@PathVariable Long orderId) {
        requireRole(1);
        Long shopId = currentShopId();
        ordersService.approveRefund(shopId, orderId);
        return Result.success("操作成功", null);
    }

    @PostMapping("/merchant/refund/{orderId}/refuse")
    @Operation(summary = "商家拒绝退款")
    public Result<Void> refuseRefund(@PathVariable Long orderId, @RequestBody OrderRefundRefuseRequest request) {
        requireRole(1);
        Long shopId = currentShopId();
        ordersService.refuseRefund(shopId, orderId, request.getReason());
        return Result.success("操作成功", null);
    }

    private Long currentUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        return userId;
    }

    private Long currentShopId() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            throw new ServiceException(ResultCode.FORBIDDEN, "请先完善店铺信息");
        }
        return shopId;
    }

    private void requireRole(Integer expectRole) {
        Map<String, Object> user = UserContext.getUser();
        Integer role = null;
        if (user != null && user.get("role") != null) {
            role = Integer.valueOf(String.valueOf(user.get("role")));
        }
        if (!expectRole.equals(role)) {
            throw new ServiceException(ResultCode.FORBIDDEN, "无权限访问");
        }
    }
}
