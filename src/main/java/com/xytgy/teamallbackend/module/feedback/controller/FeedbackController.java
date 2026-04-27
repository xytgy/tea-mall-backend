package com.xytgy.teamallbackend.module.feedback.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.module.feedback.dto.FeedbackSubmitRequest;
import com.xytgy.teamallbackend.module.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "意见反馈")
@RequestMapping("/api/feedback")
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    @PostMapping("/submit")
    @Operation(summary = "提交意见反馈")
    public Result<Void> submit(@RequestBody FeedbackSubmitRequest request) {
        Long userId = UserContext.getCurrentUserId(); // 若未登录返回null，不抛出异常，因此也支持游客
        feedbackService.submitFeedback(userId, request);
        return Result.success("操作成功", null);
    }
}
