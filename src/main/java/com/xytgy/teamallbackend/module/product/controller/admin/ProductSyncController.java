package com.xytgy.teamallbackend.module.product.controller.admin;

import com.xytgy.teamallbackend.common.BaseController;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.module.product.service.ProductSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 商品数据同步管理控制器（管理员专用）。
 * <p>
 * 提供 MySQL 到 Elasticsearch 的数据同步管理接口，
 * 包括触发全量同步和查询同步状态。
 */
@RestController
@RequestMapping("/admin/product/sync")
@Tag(name = "商品同步管理")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class ProductSyncController extends BaseController {

    private final ProductSyncService productSyncService;

    /**
     * 触发全量同步。
     * <p>
     * 将 MySQL 中所有上架且审核通过的商品异步同步到 Elasticsearch。
     * 同步任务在后台异步执行，不会阻塞 API 响应。
     * 如果已有同步任务在执行，本次请求会被跳过。
     *
     * @return 操作结果
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/full")
    @Operation(summary = "触发全量同步", description = "将 MySQL 商品数据全量同步到 Elasticsearch（异步执行）")
    public Result<Map<String, String>> triggerFullSync() {
        productSyncService.fullSyncAsync();
        return Result.success("全量同步任务已提交，请通过状态接口查看进度",
                Map.of("status", "SUBMITTED"));
    }

    /**
     * 查询同步状态。
     *
     * @return 当前同步状态
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/status")
    @Operation(summary = "查询同步状态", description = "查看 ES 数据同步的当前状态")
    public Result<Map<String, String>> getSyncStatus() {
        String status = productSyncService.getSyncStatus();
        return Result.success(Map.of("status", status));
    }
}
