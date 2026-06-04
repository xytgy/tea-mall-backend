package com.xytgy.teamallbackend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类，从 SecurityContextHolder 中提取当前登录用户信息。
 * <p>
 * 所有业务层获取当前用户的方式统一通过此类，避免直接操作 SecurityContextHolder。
 * 未登录时各方法返回 null，调用方自行决定是否抛出异常。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getUserId();
        }
        return null;
    }

    public static Integer getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getRole();
        }
        return null;
    }

    public static Long getCurrentShopId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getShopId();
        }
        return null;
    }
}
