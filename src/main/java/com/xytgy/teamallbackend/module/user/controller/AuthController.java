package com.xytgy.teamallbackend.module.user.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.module.user.dto.LoginRequest;
import com.xytgy.teamallbackend.module.user.dto.RegisterRequest;
import com.xytgy.teamallbackend.module.user.vo.LoginResponse;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.exception.ServiceException;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "登录注册")
@RequestMapping("api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse data = userService.login(request);
            return Result.success("登录成功", data);
        } catch (ServiceException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            return Result.error(401, e.getMessage());
        }
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody RegisterRequest request) {
        try {
            userService.register(request);
            return Result.success("注册成功", null);
        } catch (ServiceException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            return Result.error(409, e.getMessage());
        }
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        try {
            userService.logout();
            return Result.success("退出成功", null);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }
}
