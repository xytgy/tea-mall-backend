package com.xytgy.teamallbackend.module.teacircle.controller;

import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.security.SecurityUtils;
import com.xytgy.teamallbackend.module.teacircle.service.TeaFollowService;
import com.xytgy.teamallbackend.module.teacircle.vo.SimpleUserVO;
import com.xytgy.teamallbackend.module.teacircle.vo.UserProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Tag(name = "茶友圈 - 关注")
@RequestMapping("/api/tea-circle/users")
@RequiredArgsConstructor
public class TeaCircleFollowController {

    private final TeaFollowService teaFollowService;

    @PostMapping("/{userId}/follow")
    @Operation(summary = "关注/取消关注")
    public Result<Map<String, Boolean>> toggleFollow(@PathVariable Long userId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return Result.success(teaFollowService.toggleFollow(currentUserId, userId));
    }

    @GetMapping("/following")
    @Operation(summary = "获取关注列表")
    public Result<PageResult<SimpleUserVO>> getFollowing(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int pageSize) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return Result.success(teaFollowService.getFollowingList(currentUserId, page, pageSize));
    }

    @GetMapping("/followers")
    @Operation(summary = "获取粉丝列表")
    public Result<PageResult<SimpleUserVO>> getFollowers(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int pageSize) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return Result.success(teaFollowService.getFollowersList(currentUserId, page, pageSize));
    }
    
    @GetMapping("/{userId}/profile")
    @Operation(summary = "获取用户详细统计资料")
    public Result<UserProfileVO> getUserProfile(@PathVariable Long userId) {
        Long currentUserId = null;
        try {
            currentUserId = SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            // 未登录时允许查看，但 isFollowing 会返回 false
        }
        return Result.success(teaFollowService.getUserProfile(currentUserId, userId));
    }
}
