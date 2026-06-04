package com.xytgy.teamallbackend.config.security;

import com.xytgy.teamallbackend.common.UserRole;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.module.shop.service.ShopService;
import com.xytgy.teamallbackend.security.JwtAuthenticationToken;
import com.xytgy.teamallbackend.utils.JwtUtils;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.security.SecurityConstants;

import java.io.IOException;
import io.jsonwebtoken.security.SignatureException;
import java.util.List;
import java.util.Map;

/**
 * JWT 认证过滤器，替代原 JwtInterceptor。
 * <p>
 * 执行流程：
 * 1. 白名单路径直接放行（shouldNotFilter）
 * 2. 从 Authorization 头提取 Bearer Token
 * 3. 解析 JWT，校验 Redis 在线状态和用户启用状态
 * 4. 构建 JwtAuthenticationToken 写入 SecurityContextHolder
 * 5. 无 Token 时放行（由后续 @PreAuthorize 决定是否拒绝）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final ShopService shopService;

    private static final String LOGIN_USER_KEY_PREFIX = "login:user:";
    private static final String ROLE_USER = "ROLE_USER";
    private static final String ROLE_MERCHANT = "ROLE_MERCHANT";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SecurityConstants.isPublicPath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            Map<String, Object> claims = jwtUtils.parseToken(token);

            Object userIdObj = claims.get("id");
            if (userIdObj == null) {
                writeUnauthorized(response, 401, "Token 格式错误");
                return;
            }

            Long userId = Long.valueOf(userIdObj.toString());

            // 校验用户是否仍在登录状态（Redis 中存在在线 key）
            Boolean hasKey = stringRedisTemplate.hasKey(LOGIN_USER_KEY_PREFIX + userId);
            if (Boolean.FALSE.equals(hasKey)) {
                writeUnauthorized(response, 401, "登录已失效，请重新登录");
                return;
            }

            // 校验用户是否被管理员禁用
            if (!userService.isUserEnabled(userId)) {
                writeUnauthorized(response, 401, "账号已禁用，请联系管理员");
                return;
            }

            // 从 JWT claims 中提取完整用户信息，构建自定义 Authentication
            Integer role = (Integer) claims.get("role");
            Object shopIdObj = claims.get("shopId");
            Long shopId = shopIdObj != null ? Long.valueOf(shopIdObj.toString()) : null;

            // 商家用户：校验 JWT 中的 shopId 是否与数据库一致，防止 shopId 伪造
            if (role != null && role == UserRole.MERCHANT.getCode() && shopId != null) {
                Long actualShopId = shopService.getShopIdByUserId(userId);
                if (actualShopId == null || !actualShopId.equals(shopId)) {
                    log.warn("商家 shopId 不一致: userId={}, jwtShopId={}, dbShopId={}", userId, shopId, actualShopId);
                    writeUnauthorized(response, 401, "店铺信息异常，请重新登录");
                    return;
                }
            }

            String authority = toAuthority(role);
            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(authority));

            JwtAuthenticationToken authentication =
                    new JwtAuthenticationToken(userId, role, shopId, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException e) {
            log.debug("Token 已过期: {}", e.getMessage());
            writeUnauthorized(response, 10002, "TOKEN_EXPIRED");
        } catch (MalformedJwtException | SignatureException e) {
            log.warn("Token 无效: {}", e.getMessage());
            writeUnauthorized(response, 401, "Token 无效");
        } catch (Exception e) {
            log.error("JWT 验证失败", e);
            writeUnauthorized(response, 401, "登录状态验证失败");
        }
    }

    /** 数据库角色码映射为 Spring Security 标准角色前缀 */
    private String toAuthority(Integer role) {
        if (role == null) return ROLE_USER;
        return switch (role) {
            case UserRole.CODE_ADMIN -> ROLE_ADMIN;
            case UserRole.CODE_MERCHANT -> ROLE_MERCHANT;
            default -> ROLE_USER;
        };
    }

    private void writeUnauthorized(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = Map.of("code", code, "message", msg, "data", null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
