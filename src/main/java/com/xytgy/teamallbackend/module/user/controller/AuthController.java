package com.xytgy.teamallbackend.module.user.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.user.dto.LoginRequest;
import com.xytgy.teamallbackend.module.user.dto.RegisterRequest;
import com.xytgy.teamallbackend.module.user.vo.LoginResponse;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.properties.RateLimitProperties;
import com.xytgy.teamallbackend.ratelimit.RateLimitService;
import com.xytgy.teamallbackend.utils.RequestUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final RateLimitProperties rateLimitProperties;

    @PostMapping("/login")
    @Operation(summary = "登录接口")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        String clientIp = RequestUtils.getClientIp(httpRequest);
        String userAccount = request.getUserAccount();

        // 1. 账号锁定预检（仅检查锁定状态，不记录次数，防枚举）
        long lockRemaining = rateLimitService.getAccountLockRemaining(userAccount);
        if (lockRemaining > 0 && userService.existsByAccount(userAccount)) {
            throw new ServiceException(ResultCode.ACCOUNT_LOCKED,
                    "账号已被临时锁定，请" + lockRemaining + "秒后重试");
        }

        // 2. IP 维度限速，防御分布式密码喷射攻击
        long ipResult = rateLimitService.checkIpRate(clientIp);
        setRateLimitHeaders(httpResponse, ipResult, rateLimitProperties.getLogin().getIpMaxPerMinute());

        if (!RateLimitService.isAllowed(ipResult)) {
            if (RateLimitService.isLocked(ipResult)) {
                throw new ServiceException(ResultCode.IP_LOCKED, "当前网络已被临时限制，请稍后再试");
            }
            throw new ServiceException(ResultCode.TOO_MANY_REQUESTS, "当前网络登录请求过于频繁，请稍后再试");
        }

        // 3. 登录（内部会检查账号级限流）
        LoginResponse data = userService.login(request, clientIp);
        return Result.success("登录成功", data);
    }

    @PostMapping("/register")
    @Operation(summary = "注册接口")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request,
                                  HttpServletRequest httpRequest,
                                  HttpServletResponse httpResponse) {
        String clientIp = RequestUtils.getClientIp(httpRequest);
        long ipRate = rateLimitService.checkIpRate(clientIp);
        setRateLimitHeaders(httpResponse, ipRate, rateLimitProperties.getLogin().getIpMaxPerMinute());

        if (!RateLimitService.isAllowed(ipRate)) {
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

    private static void setRateLimitHeaders(HttpServletResponse response, long remaining, int limit) {
        if (remaining >= 0) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Reset", "60");
        }
    }
}
