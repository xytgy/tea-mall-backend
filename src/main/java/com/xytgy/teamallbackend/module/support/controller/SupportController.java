package com.xytgy.teamallbackend.module.support.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.security.SecurityUtils;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.support.dto.SupportCreateRequest;
import com.xytgy.teamallbackend.module.support.service.SupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/support")
@Tag(name = "我的咨询(工单)")
@SecurityRequirement(name = "BearerAuth")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    @PostMapping("/create")
    @Operation(summary = "提交新咨询")
    public Result<Void> createSupport(@Validated @RequestBody SupportCreateRequest request) {
        Long userId = currentUserId();
        supportService.createSupport(userId, request);
        return Result.success("咨询提交成功", null);
    }

    @GetMapping("/list")
    @Operation(summary = "获取咨询列表")
    public Result<Map<String, Object>> listSupports(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "category", required = false) String category) {
        Long userId = currentUserId();
        return Result.success(supportService.listSupports(userId, page, pageSize, category));
    }

    @GetMapping("/stats")
    @Operation(summary = "获取咨询分类统计信息")
    public Result<Map<String, Integer>> getSupportStats() {
        Long userId = currentUserId();
        return Result.success(supportService.getSupportStats(userId));
    }

    private Long currentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        return userId;
    }
}
