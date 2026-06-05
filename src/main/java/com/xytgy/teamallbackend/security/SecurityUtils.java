package com.xytgy.teamallbackend.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 安全上下文工具类，从 SecurityContextHolder 中提取当前登录用户信息。
 * <p>
 * 所有业务层获取当前用户的方式统一通过此类，避免直接操作 SecurityContextHolder。
 * 未登录时各 getXxx() 方法返回 null，调用方自行决定是否抛出异常。
 */
@Slf4j
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前认证对象（如果是 JwtAuthenticationToken）。
     * 非 JWT 认证（如匿名访问）返回 Optional.empty()。
     */
    public static Optional<JwtAuthenticationToken> getCurrentToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return Optional.of(token);
        }
        // 已认证但不是预期的 JWT 类型，记录警告帮助排查
        if (auth != null && auth.isAuthenticated()
                && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            log.warn("当前认证对象非 JwtAuthenticationToken，实际类型: {}", auth.getClass().getName());
        }
        return Optional.empty();
    }

    /**
     * 判断当前是否已通过 JWT 认证。
     */
    public static boolean isAuthenticated() {
        return getCurrentToken().isPresent();
    }

    public static Long getCurrentUserId() {
        return getCurrentToken().map(JwtAuthenticationToken::getUserId).orElse(null);
    }

    public static Integer getCurrentRole() {
        return getCurrentToken().map(JwtAuthenticationToken::getRole).orElse(null);
    }

    public static Long getCurrentShopId() {
        return getCurrentToken().map(JwtAuthenticationToken::getShopId).orElse(null);
    }
}
