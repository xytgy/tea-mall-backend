package com.xytgy.teamallbackend.module.flashsale.controller;

import com.xytgy.teamallbackend.common.BaseController;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.module.flashsale.dto.CaptchaVerifyRequest;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleBuyRequest;
import com.xytgy.teamallbackend.module.flashsale.service.FlashSaleCoreServicePractice;
import com.xytgy.teamallbackend.module.flashsale.service.FlashSaleService;
import com.xytgy.teamallbackend.module.flashsale.service.FlashSaleService.CaptchaResult;
import com.xytgy.teamallbackend.module.flashsale.service.FlashSaleService.FlashSaleBuyResult;
import com.xytgy.teamallbackend.module.flashsale.vo.FlashSaleProductVO;
import com.xytgy.teamallbackend.module.flashsale.vo.FlashSaleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flash-sale")
@Tag(name = "秒杀活动")
@SecurityRequirement(name = "BearerAuth")
@Validated
@RequiredArgsConstructor
public class FlashSaleController extends BaseController {

    private final FlashSaleService flashSaleService;
    private final FlashSaleCoreServicePractice flashSaleCoreServicePractice;
    @GetMapping("/list")
    @Operation(summary = "秒杀活动列表")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "401", description = "未登录")
    public Result<List<FlashSaleVO>> list() {
        return Result.success(flashSaleService.listActiveSales());
    }

    @GetMapping("/{id}/products")
    @Operation(summary = "获取活动商品列表")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "404", description = "活动不存在")
    public Result<List<FlashSaleProductVO>> products(@PathVariable Long id) {
        return Result.success(flashSaleService.getProducts(id));
    }

    @GetMapping("/captcha")
    @Operation(summary = "获取秒杀验证码图片")
    @ApiResponse(
            responseCode = "200",
            description = "成功",
            content = @Content(schema = @Schema(type = "object"))
    )
    public Result<CaptchaResult> captcha() {
        Long userId = currentUserId();
        return Result.success(flashSaleService.generateCaptcha(userId));
    }

    @GetMapping("/captchapratice")
    @Operation(summary = "练习获取秒杀验证码图片")
    @ApiResponse(
            responseCode = "200",
            description = "成功",
            content = @Content(schema = @Schema(type = "object"))
    )
    public Result<CaptchaResult> captchapratice() {

        return Result.success(flashSaleCoreServicePractice.generateCaptchaPractice(currentUserId()));
    }

    @PostMapping("/captcha/verify")
    @Operation(summary = "验证码换秒杀 Token")
    @ApiResponse(responseCode = "200", description = "验证通过，返回秒杀 token")
    @ApiResponse(responseCode = "400", description = "验证码错误或已过期")
    public Result<String> verifyCaptcha(@Valid @RequestBody CaptchaVerifyRequest request) {
        Long userId = currentUserId();
        return Result.success(flashSaleService.verifyCaptcha(userId, request));
    }

    @PostMapping("/captcha/verifyPratice")
    @Operation(summary = "练习版验证码换秒杀 Token")
    @ApiResponse(responseCode = "200", description = "验证通过，返回秒杀 token")
    @ApiResponse(responseCode = "400", description = "验证码错误或已过期")
    public Result<String> verifyCaptchaPratice(@Valid @RequestBody CaptchaVerifyRequest request) {
        Long userId = currentUserId();
        return Result.success(flashSaleCoreServicePractice.verifyCaptchaPractice(userId, request));
    }


    @PostMapping("/buy")
    @Operation(summary = "立即抢购")
    @ApiResponse(responseCode = "200", description = "下单成功")
    @ApiResponse(responseCode = "400", description = "库存不足 / 参数错误")
    @ApiResponse(responseCode = "429", description = "请求过于频繁")
    public Result<FlashSaleBuyResult> buy(@Valid @RequestBody FlashSaleBuyRequest request) {
        Long userId = currentUserId();
        return Result.success(flashSaleService.buy(userId, request));
    }

    @PostMapping("/buypratice")
    @Operation(summary = "练习版立即抢购")
    @ApiResponse(responseCode = "200", description = "下单成功")
    @ApiResponse(responseCode = "400", description = "库存不足 / 参数错误")
    @ApiResponse(responseCode = "429", description = "请求过于频繁")
    public Result<FlashSaleBuyResult> buypratice(@Valid @RequestBody FlashSaleBuyRequest request) {
        Long userId = currentUserId();
        return Result.success(flashSaleCoreServicePractice.buy(userId, request));
    }

    @GetMapping("/result/{orderId}")
    @Operation(summary = "查询秒杀订单结果")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "404", description = "订单不存在")
    public Result<Map<String, Object>> result(@PathVariable Long orderId) {
        Long userId = currentUserId();
        // 写后读一致性：秒杀下单后立即查询，强制读主库避免从库延迟导致"订单不存在"
        // 当前 @ReadOnly AOP 未在此方法上，自然走主库，此处添加注释明确设计意图
        return Result.success(flashSaleService.getOrderResult(userId, orderId));
    }

    @PostMapping("/appeal")
    @Operation(summary = "限流申诉解封")
    @ApiResponse(responseCode = "200", description = "申诉成功")
    @ApiResponse(responseCode = "400", description = "未被限制或无失败订单")
    public Result<FlashSaleBuyResult> appeal(@RequestParam Long flashSaleId) {
        Long userId = currentUserId();
        return Result.success(flashSaleService.appeal(userId, flashSaleId));
    }
}
