package com.xytgy.teamallbackend.module.flashsale.controller;

import com.xytgy.teamallbackend.common.BaseController;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleAddProductRequest;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleCreateRequest;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleRestockRequest;
import com.xytgy.teamallbackend.module.flashsale.dto.FlashSaleStatusRequest;
import com.xytgy.teamallbackend.module.flashsale.service.FlashSaleService;
import com.xytgy.teamallbackend.module.flashsale.vo.FlashSaleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/flash-sale")
@Tag(name = "秒杀管理")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@RequiredArgsConstructor
public class FlashSaleAdminController extends BaseController {

    private final FlashSaleService flashSaleService;

    @GetMapping("/list")
    @Operation(summary = "获取秒杀活动列表")
    @ApiResponse(responseCode = "200", description = "成功")
    public Result<List<FlashSaleVO>> list() {
        return Result.success(flashSaleService.listAllSales());
    }


    @PostMapping("/create")
    @Operation(summary = "创建秒杀活动")
    @ApiResponse(responseCode = "200", description = "创建成功")
    @ApiResponse(responseCode = "400", description = "参数错误")
    public Result<Long> create(@Valid @RequestBody FlashSaleCreateRequest request) {
        Long operatorId = currentUserId();
        Long id = flashSaleService.createFlashSale(request, operatorId);
        return Result.success("创建成功", id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取秒杀活动详情")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "404", description = "活动不存在")
    public Result<FlashSaleVO> getDetail(@PathVariable Long id) {
        return Result.success(flashSaleService.getDetail(id));
    }

    @PostMapping("/{id}/products")
    @Operation(summary = "添加商品到秒杀活动")
    @ApiResponse(responseCode = "200", description = "添加成功")
    @ApiResponse(responseCode = "400", description = "参数错误")
    public Result<Void> addProduct(@PathVariable Long id,
                                   @Valid @RequestBody FlashSaleAddProductRequest request) {
        Long operatorId = currentUserId();
        flashSaleService.addProduct(id, request, operatorId);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "修改秒杀活动状态")
    @ApiResponse(responseCode = "200", description = "修改成功")
    @ApiResponse(responseCode = "400", description = "参数错误")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody FlashSaleStatusRequest request) {
        Long operatorId = currentUserId();
        flashSaleService.updateStatus(id, request.getStatus(), operatorId);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除秒杀活动")
    @ApiResponse(responseCode = "200", description = "删除成功")
    @ApiResponse(responseCode = "404", description = "活动不存在")
    public Result<Void> delete(@PathVariable Long id) {
        Long operatorId = currentUserId();
        flashSaleService.deleteFlashSale(id, operatorId);
        return Result.success();
    }

    @PostMapping("/warmup/{id}")
    @Operation(summary = "预热库存到 Redis + 本地缓存")
    @ApiResponse(responseCode = "200", description = "预热成功")
    @ApiResponse(responseCode = "404", description = "活动不存在")
    public Result<Void> warmup(@PathVariable Long id) {
        Long operatorId = currentUserId();
        flashSaleService.warmup(id, operatorId);
        return Result.success();
    }

    @PostMapping("/restock")
    @Operation(summary = "动态补货")
    @ApiResponse(responseCode = "200", description = "补货成功")
    @ApiResponse(responseCode = "400", description = "参数错误")
    public Result<Void> restock(@Valid @RequestBody FlashSaleRestockRequest request) {
        Long operatorId = currentUserId();
        flashSaleService.restock(request.getProductId(), request.getQuantity(), operatorId);
        return Result.success();
    }

    @PostMapping("/cleanup/{id}")
    @Operation(summary = "清理活动 Redis 数据")
    @ApiResponse(responseCode = "200", description = "清理成功")
    public Result<Void> cleanup(@PathVariable Long id) {
        Long operatorId = currentUserId();
        flashSaleService.cleanup(id, operatorId);
        return Result.success();
    }

    @PostMapping("/whitelist")
    @Operation(summary = "添加白名单")
    @ApiResponse(responseCode = "200", description = "添加成功")
    @ApiResponse(responseCode = "400", description = "参数错误")
    public Result<Void> whitelist(
            @RequestParam Long flashSaleId,
            @RequestBody List<Long> userIds) {
        Long operatorId = currentUserId();
        flashSaleService.addWhitelist(flashSaleId, userIds, operatorId);
        return Result.success();
    }

    @GetMapping("/dead-letters")
    @Operation(summary = "死信订单列表")
    @ApiResponse(responseCode = "200", description = "成功")
    public Result<Map<String, Object>> deadLetters(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(flashSaleService.getFailedOrders(page, size));
    }

    @PostMapping("/dead-letters/{id}/retry")
    @Operation(summary = "手动重试失败订单")
    @ApiResponse(responseCode = "200", description = "重试成功")
    @ApiResponse(responseCode = "400", description = "订单状态异常")
    public Result<Void> retryDeadLetter(@PathVariable Long id) {
        Long operatorId = currentUserId();
        flashSaleService.retryFailedOrder(id, operatorId);
        return Result.success();
    }

    @PostMapping("/dead-letters/{id}/cancel")
    @Operation(summary = "作废失败订单")
    @ApiResponse(responseCode = "200", description = "作废成功")
    @ApiResponse(responseCode = "400", description = "订单状态异常")
    public Result<Void> cancelDeadLetter(@PathVariable Long id) {
        Long operatorId = currentUserId();
        flashSaleService.cancelFailedOrder(id, operatorId);
        return Result.success();
    }

    @PutMapping("/rate")
    @Operation(summary = "动态调整限流 QPS 阈值（实时生效，多实例共享）")
    @ApiResponse(responseCode = "200", description = "调整成功")
    public Result<Void> updateRate(
            @RequestParam(required = false) Long frequentThreshold,
            @RequestParam(required = false) Long maliciousThreshold,
            @RequestParam(required = false) Long blacklistMinutes) {
        Long operatorId = currentUserId();
        flashSaleService.updateRateConfig(frequentThreshold, maliciousThreshold, blacklistMinutes, operatorId);
        return Result.success();
    }

    @GetMapping("/rate")
    @Operation(summary = "查询当前限流配置")
    @ApiResponse(responseCode = "200", description = "成功")
    public Result<Map<String, Object>> getRate() {
        return Result.success(flashSaleService.getRateConfig());
    }

    @PostMapping("/dead-letters/{id}/compensate")
    @Operation(summary = "P1 补偿：发放优惠券")
    @ApiResponse(responseCode = "200", description = "补偿成功")
    @ApiResponse(responseCode = "400", description = "订单状态异常")
    public Result<Void> compensate(
            @PathVariable Long id,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String remark) {
        Long operatorId = currentUserId();
        flashSaleService.compensateFailedOrder(id, amount, remark, operatorId);
        return Result.success();
    }

    @PostMapping("/dead-letters/{id}/manual")
    @Operation(summary = "P2 人工处理：标记为已人工处理")
    @ApiResponse(responseCode = "200", description = "处理成功")
    @ApiResponse(responseCode = "400", description = "订单状态异常")
    public Result<Void> manualProcess(
            @PathVariable Long id,
            @RequestParam(required = false) String remark) {
        Long operatorId = currentUserId();
        flashSaleService.manualProcessFailedOrder(id, remark, operatorId);
        return Result.success();
    }
}
