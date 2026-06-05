package com.xytgy.teamallbackend.module.user.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.user.dto.LoginRequest;
import com.xytgy.teamallbackend.module.user.dto.RegisterRequest;
import com.xytgy.teamallbackend.module.user.vo.LoginResponse;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.ratelimit.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "登录注册")
@RequestMapping("/api/auth")
@Validated
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final RateLimitService rateLimitService;

    @PostMapping("/login")
    @Operation(summary = "登录接口")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest) {
        // IP 维度限速，防御分布式密码喷射攻击
        String clientIp = getClientIp(httpRequest);
        if (rateLimitService.isIpLocked(clientIp)) {
            throw new ServiceException(ResultCode.TOO_MANY_REQUESTS, "当前网络已被临时限制访问，请稍后再试");
        }
        if (!rateLimitService.checkIpRate(clientIp)) {
            throw new ServiceException(ResultCode.TOO_MANY_REQUESTS, "当前网络登录请求过于频繁，请稍后再试");
        }

        LoginResponse data = userService.login(request, clientIp);
        return Result.success("登录成功", data);
    }

    @PostMapping("/register")
    @Operation(summary = "注册接口")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request,
                                  HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        if (!rateLimitService.checkIpRate(clientIp)) {
            throw new ServiceException(ResultCode.TOO_MANY_REQUESTS, "当前网络请求过于频繁，请稍后再试");
        }
        userService.register(request);
        return Result.success("注册成功", null);
    }

    @PostMapping("/logout")
    @Operation(summary = "退出接口")
    public Result<Void> logout() {
        userService.logout();
        return Result.success("退出成功", null);
    }

    /**
     * 从请求中提取客户端真实 IP，兼容反向代理场景
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能包含多个 IP，取第一个（最初客户端）
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
