package com.xytgy.teamallbackend.security;

public final class SecurityConstants {

    private SecurityConstants() {
    }

    /**
     * 公开访问路径（无需认证）
     */
    public static final String[] PUBLIC_PATHS = {
            "/api/auth/login",
            "/api/auth/register",
            "/api/user/refresh/token",
            "/api/product/list",
            "/api/product/list/**",
            "/api/product/reviews",
            "/api/store/**",
            "/api/tea-circle/topics",
            "/api/tea-circle/campaigns/latest",
            "/api/feedback/submit",
            "/uploads/**",
            "/ws/**"
    };

    /**
     * API 文档路径（仅在开发环境开放，生产环境需通过配置禁用）
     */
    public static final String[] DOCS_PATHS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/doc.html",
            "/webjars/**"
    };

    public static boolean isPublicPath(String path) {
        for (String pattern : PUBLIC_PATHS) {
            if (pattern.endsWith("/**")) {
                if (path.startsWith(pattern.substring(0, pattern.length() - 3))) {
                    return true;
                }
            } else if (pattern.equals(path)) {
                return true;
            }
        }
        return false;
    }
}