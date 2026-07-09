package com.xytgy.teamallbackend.module.user.controller;

import com.xytgy.teamallbackend.common.BaseController;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.module.user.dto.AdminUserAddRequest;
import com.xytgy.teamallbackend.module.user.dto.UserStatusRequest;
import com.xytgy.teamallbackend.security.SecurityUtils;
import jakarta.validation.Valid;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.module.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@Tag(name = "管理员接口")
@RequestMapping("/api/user/admin")
@Validated
@RequiredArgsConstructor
public class AdminUserController extends BaseController {

    private final UserService userService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/add")
    @Operation(summary = "新增用户接口")
    public Result<Void> add(@Valid @RequestBody AdminUserAddRequest request) {
        Long userId = userService.addUserByAdmin(request);
        // 审计日志：记录管理员创建用户的敏感操作
        log.info("[AUDIT] 管理员 {} 创建用户: account={}, role={}, status={}, newUserId={}",
                SecurityUtils.getCurrentUserId(), request.getUserAccount(),
                request.getRole(), request.getStatus(), userId);
        return Result.success(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/list")
    @Operation(summary = "获取用户列表")
    public Result<List<UserVO>> list() {
        return Result.success(userService.listUsersByAdmin());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/status")
    @Operation(summary = "更新用户状态")
    public Result<Void> updateStatus(@Valid @RequestBody UserStatusRequest request) {
        userService.updateUserStatusByAdmin(request.getId(), request.getStatus());
        // 审计日志：记录管理员修改用户状态的敏感操作
        log.info("[AUDIT] 管理员 {} 修改用户状态: targetUserId={}, newStatus={}",
                SecurityUtils.getCurrentUserId(), request.getId(), request.getStatus());
        return Result.success(null);
    }

}
