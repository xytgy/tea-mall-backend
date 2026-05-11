package com.xytgy.teamallbackend.module.feedback.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.feedback.dto.FeedbackSubmitRequest;
import com.xytgy.teamallbackend.module.feedback.dto.FeedbackStatusRequest;
import com.xytgy.teamallbackend.module.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Tag(name = "意见反馈")
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/submit")
    @Operation(summary = "提交意见反馈")
    public Result<Void> submit(@RequestBody FeedbackSubmitRequest request) {
        Long userId = UserContext.getCurrentUserId(); // 若未登录返回null，不抛出异常，因此也支持游客
        feedbackService.submitFeedback(userId, request);
        return Result.success("操作成功", null);
    }

    @GetMapping("/admin/list")
    @Operation(summary = "管理员获取反馈列表")
    public Result<Map<String, Object>> adminList(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "type", required = false) String type) {
        requireRole(2);
        return Result.success(feedbackService.listFeedback(page, pageSize, status, type));
    }

    @PutMapping("/admin/status")
    @Operation(summary = "管理员修改反馈状态")
    public Result<Void> updateStatus(@RequestBody FeedbackStatusRequest request) {
        requireRole(2);
        feedbackService.updateStatus(request.getId(), request.getStatus());
        return Result.success("操作成功", null);
    }

    private void requireRole(Integer expectRole) {
        Integer role = UserContext.getRole();
        if (!expectRole.equals(role)) {
            throw new ServiceException(ResultCode.FORBIDDEN, "无权限访问");
        }
    }
}
