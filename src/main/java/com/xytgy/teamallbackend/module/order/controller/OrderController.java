package com.xytgy.teamallbackend.module.order.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.module.order.dto.OrderCreateRequest;
import com.xytgy.teamallbackend.module.order.dto.OrderPayRequest;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.order.service.OrdersService;
import com.xytgy.teamallbackend.module.order.vo.CreateOrderVO;
import com.xytgy.teamallbackend.module.order.vo.MerchantOrderVO;
import com.xytgy.teamallbackend.module.order.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
@Tag(name = "订单")
@SecurityRequirement(name = "BearerAuth")
public class OrderController {

    @Autowired
    private OrdersService ordersService;

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
    public Result<List<OrderVO>> list() {
        Long userId = currentUserId();
        return Result.success(ordersService.listOrders(userId));
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

    @GetMapping("/merchant/list")
    @Operation(summary = "商家获取自己的订单列表")
    public Result<List<MerchantOrderVO>> merchantList() {
        Long merchantId = currentUserId();
        requireRole(1);
        return Result.success(ordersService.listMerchantOrders(merchantId));
    }

    @PostMapping("/merchant/deliver/{orderId}")
    @Operation(summary = "商家对订单进行发货")
    public Result<Void> deliver(@PathVariable Long orderId) {
        Long merchantId = currentUserId();
        requireRole(1);
        ordersService.deliverOrder(merchantId, orderId);
        return Result.success(null);
    }

    private Long currentUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        return userId;
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
