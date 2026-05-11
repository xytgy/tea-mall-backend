package com.xytgy.teamallbackend.module.user.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.user.dto.LoginRequest;
import com.xytgy.teamallbackend.module.user.dto.RegisterRequest;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.module.user.vo.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping ("api/auth")
@RequiredArgsConstructor
public class AuthController {


    private final UserService userService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse data = userService.login(request);
            return Result.success("登录成功",data);
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
            return Result.success();
        } catch (ServiceException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            return Result.error(409, e.getMessage());
        }
    }


}