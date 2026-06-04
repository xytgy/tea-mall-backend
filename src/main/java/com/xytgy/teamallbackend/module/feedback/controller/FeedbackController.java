package com.xytgy.teamallbackend.module.feedback.controller;

import com.xytgy.teamallbackend.common.BaseController;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.security.SecurityUtils;
import com.xytgy.teamallbackend.module.feedback.dto.FeedbackSubmitRequest;
import com.xytgy.teamallbackend.module.feedback.dto.FeedbackStatusRequest;
import com.xytgy.teamallbackend.module.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Tag(name = "意见反馈")
@RequestMapping("/api/feedback")
@Validated
@RequiredArgsConstructor
public class FeedbackController extends BaseController {

    private final FeedbackService feedbackService;

    @PostMapping("/submit")
    @Operation(summary = "提交意见反馈")
    public Result<Void> submit(@Valid @RequestBody FeedbackSubmitRequest request) {
        Long userId = SecurityUtils.getCurrentUserId(); // 若未登录返回null，不抛出异常，因此也支持游客
        feedbackService.submitFeedback(userId, request);
        return Result.success("操作成功", null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/list")
    @Operation(summary = "管理员获取反馈列表")
    public Result<Map<String, Object>> adminList(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "type", required = false) String type) {
        return Result.success(feedbackService.listFeedback(page, pageSize, status, type));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/status")
    @Operation(summary = "管理员修改反馈状态")
    public Result<Void> updateStatus(@Valid @RequestBody FeedbackStatusRequest request) {
        feedbackService.updateStatus(request.getId(), request.getStatus());
        return Result.success("操作成功", null);
    }

}
