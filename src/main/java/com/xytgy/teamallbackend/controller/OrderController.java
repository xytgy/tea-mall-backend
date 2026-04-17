package com.xytgy.teamallbackend.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.dto.OrderCreateRequest;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.service.OrdersService;
import com.xytgy.teamallbackend.vo.CreateOrderVO;
import com.xytgy.teamallbackend.vo.OrderVO;
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

    private Long currentUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(401, "未登录");
        }
        return userId;
    }
}
