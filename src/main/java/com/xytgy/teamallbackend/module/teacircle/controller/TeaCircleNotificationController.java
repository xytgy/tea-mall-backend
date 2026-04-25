package com.xytgy.teamallbackend.module.teacircle.controller;

import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.module.teacircle.service.TeaNotificationService;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaNotificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Tag(name = "茶友圈 - 消息通知")
@RequestMapping("/api/tea-circle/notifications")
public class TeaCircleNotificationController {

    @Autowired
    private TeaNotificationService teaNotificationService;

    @GetMapping("/unread-count")
    @Operation(summary = "获取未读消息数")
    public Result<Map<String, Integer>> getUnreadCount() {
        Long userId = UserContext.getCurrentUserId();
        return Result.success(teaNotificationService.getUnreadCount(userId));
    }

    @GetMapping
    @Operation(summary = "获取消息列表")
    public Result<PageResult<TeaNotificationVO>> list(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = UserContext.getCurrentUserId();
        return Result.success(teaNotificationService.listNotifications(userId, page, pageSize));
    }

    @PutMapping("/read")
    @Operation(summary = "标记消息为已读")
    public Result<Void> markAsRead() {
        Long userId = UserContext.getCurrentUserId();
        teaNotificationService.markAsRead(userId);
        return Result.success(null);
    }
}
