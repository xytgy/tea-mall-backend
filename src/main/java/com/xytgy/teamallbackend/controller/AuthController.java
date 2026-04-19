package com.xytgy.teamallbackend.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.dto.LoginRequest;
import com.xytgy.teamallbackend.dto.RegisterRequest;
import com.xytgy.teamallbackend.vo.LoginResponse;
import com.xytgy.teamallbackend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse data = userService.login(request.getUserAccount(), request.getPassword());
            return Result.success("登录成功", data);
        } catch (Exception e) {
            return Result.error(401, e.getMessage());
        }
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody RegisterRequest request) {
        try {
            userService.register(request.getUserAccount(), request.getPassword(), request.getPhone());
            return Result.success("注册成功", null);
        } catch (Exception e) {
            return Result.error(409, e.getMessage());
        }
    }
}
