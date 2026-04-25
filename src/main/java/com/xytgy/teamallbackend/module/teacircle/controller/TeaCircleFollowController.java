package com.xytgy.teamallbackend.module.teacircle.controller;

import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.module.teacircle.service.TeaFollowService;
import com.xytgy.teamallbackend.module.teacircle.vo.SimpleUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Tag(name = "茶友圈 - 关注")
@RequestMapping("/api/tea-circle/users")
public class TeaCircleFollowController {

    @Autowired
    private TeaFollowService teaFollowService;

    @PostMapping("/{userId}/follow")
    @Operation(summary = "关注/取消关注")
    public Result<Map<String, Boolean>> toggleFollow(@PathVariable Long userId) {
        Long currentUserId = UserContext.getCurrentUserId();
        return Result.success(teaFollowService.toggleFollow(currentUserId, userId));
    }

    @GetMapping("/following")
    @Operation(summary = "获取关注列表")
    public Result<PageResult<SimpleUserVO>> getFollowing(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int pageSize) {
        Long currentUserId = UserContext.getCurrentUserId();
        return Result.success(teaFollowService.getFollowingList(currentUserId, page, pageSize));
    }

    @GetMapping("/followers")
    @Operation(summary = "获取粉丝列表")
    public Result<PageResult<SimpleUserVO>> getFollowers(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int pageSize) {
        Long currentUserId = UserContext.getCurrentUserId();
        return Result.success(teaFollowService.getFollowersList(currentUserId, page, pageSize));
    }
}
