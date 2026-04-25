package com.xytgy.teamallbackend.module.user.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.module.user.vo.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "用户接口")
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/refresh/token")
    @Operation(summary = "刷新 Token 接口")
    public Result<LoginResponse> refreshToken(@RequestParam("refreshToken") String refreshToken) {
        LoginResponse data = userService.refreshToken(refreshToken);
        return Result.success("刷新成功", data);
    }
}
