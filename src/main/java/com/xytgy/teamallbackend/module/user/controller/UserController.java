package com.xytgy.teamallbackend.module.user.controller;

import com.xytgy.teamallbackend.common.BaseController;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.module.user.vo.LoginResponse;
import com.xytgy.teamallbackend.module.user.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import java.util.Map;
import com.xytgy.teamallbackend.module.user.dto.UserProfileUpdateRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.xytgy.teamallbackend.module.user.vo.UserOverviewStatsVO;

@RestController
@Tag(name = "用户接口")
@RequestMapping("/api/user")
@Validated
@RequiredArgsConstructor
public class UserController extends BaseController {

    private final UserService userService;

    @PostMapping("/refresh/token")
    @Operation(summary = "刷新 Token 接口")
    public Result<LoginResponse> refreshToken(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        LoginResponse data = userService.refreshToken(refreshToken);
        return Result.success("刷新成功", data);
    }

    @GetMapping("/stats")
    @Operation(summary = "获取用户总览统计数据")
    public Result<UserOverviewStatsVO> getUserStats() {
        Long userId = currentUserId();
        return Result.success("获取成功", userService.getUserOverviewStats(userId));
    }

    @GetMapping("/info")
    @Operation(summary = "获取当前用户信息")
    public Result<UserInfoVO> getUserInfo() {
        Long userId = currentUserId();
        UserInfoVO userInfo = userService.getUserInfo(userId);
        return Result.success("获取成功", userInfo);
    }

    @PostMapping("/avatar")
    @Operation(summary = "更新用户头像")
    public Result<String> updateAvatar(@RequestBody Map<String, String> body) {
        Long userId = currentUserId();
        String avatarBase64 = body.get("avatarBase64");
        String avatarUrl = userService.updateAvatar(userId, avatarBase64);
        return Result.success("更新成功", avatarUrl);
    }

    @PostMapping("/profile")
    @Operation(summary = "更新个人资料")
    public Result<UserInfoVO> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        Long userId = currentUserId();
        userService.updateProfile(userId, request);
        return Result.success("更新成功", userService.getUserInfo(userId));
    }
}
