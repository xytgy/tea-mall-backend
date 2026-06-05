package com.xytgy.teamallbackend.config.security;

import com.xytgy.teamallbackend.common.UserRole;
import com.xytgy.teamallbackend.module.user.dto.UserInfoCache;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.module.shop.service.ShopService;
import com.xytgy.teamallbackend.security.JwtAuthenticationToken;
import com.xytgy.teamallbackend.utils.JwtUtils;
import com.xytgy.teamallbackend.utils.RedisUtils;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.security.SecurityConstants;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * JWT 认证过滤器，替代原 JwtInterceptor。
 * <p>
 * 执行流程：
 * 1. 白名单路径直接放行（shouldNotFilter）
 * 2. 从 Authorization 头提取 Bearer Token
 * 3. 解析 JWT，从 Redis 缓存读取用户信息（L1 Caffeine + L2 Redis），缓存未命中时降级查 DB
 * 4. 构建 JwtAuthenticationToken 写入 SecurityContextHolder
 * 5. 无 Token 时放行（由后续 @PreAuthorize 决定是否拒绝）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String LOGIN_USER_KEY_PREFIX = "login:user:";
    private static final String USER_INFO_CACHE_PREFIX = "user:info:";
    private static final String ROLE_USER = "ROLE_USER";
    private static final String ROLE_MERCHANT = "ROLE_MERCHANT";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisUtils redisUtils;
    private final UserService userService;
    private final ShopService shopService;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return SecurityConstants.isPublicPath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        // OPTIONS 预检请求已由 SecurityConfig 的 CORS 配置放行，无需重复处理

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        try {
            Map<String, Object> claims = jwtUtils.parseToken(token);

            Long userId = extractLong(claims, "id");
            if (userId == null) {
                writeError(response, "Token 格式错误");
                return;
            }

            // 校验用户是否仍在登录状态（Redis 中存在在线 key）
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(LOGIN_USER_KEY_PREFIX + userId))) {
                writeError(response, "登录已失效，请重新登录");
                return;
            }

            // 从 Redis 缓存读取用户信息（L1 Caffeine + L2 Redis），缓存未命中时降级查 DB
            UserInfoCache userInfo = loadUserInfo(userId);
            if (userInfo == null) {
                writeError(response, "用户不存在");
                return;
            }
            if (!userInfo.isEnabled()) {
                writeError(response, "账号已禁用，请联系管理员");
                return;
            }

            Integer role = userInfo.getRole();
            Long shopId = userInfo.getShopId();

            // 商家用户：校验 shopId 一致性
            if (role != null && role == UserRole.MERCHANT.getCode() && shopId == null) {
                writeError(response, "店铺信息异常，请重新登录");
                return;
            }

            String authority = toAuthority(role);
            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(authority));

            JwtAuthenticationToken authentication =
                    new JwtAuthenticationToken(userId, role, shopId, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (JwtException e) {
            log.warn("Token 解析失败: type={}, message={}", e.getClass().getSimpleName(), e.getMessage());
            writeError(response, "Token 无效");
        } catch (IllegalArgumentException e) {
            log.warn("Token 参数异常: {}", e.getMessage());
            writeError(response, "Token 格式错误");
        }
    }

    /**
     * 加载用户信息：优先从 Redis 缓存读取，缓存未命中时降级查 DB 并回填缓存。
     * RedisUtils 内置 L1 (Caffeine) + L2 (Redis) 两级缓存，自动处理穿透/雪崩/击穿。
     */
    private UserInfoCache loadUserInfo(Long userId) {
        try {
            UserInfoCache cached = redisUtils.get(USER_INFO_CACHE_PREFIX + userId, UserInfoCache.class);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("Redis 读取用户缓存失败，降级查 DB: userId={}, error={}", userId, e.getMessage());
        }

        // 缓存未命中或 Redis 故障，降级查 DB
        return loadUserInfoFromDb(userId);
    }

    /**
     * 从数据库加载用户信息并回填缓存。
     */
    private UserInfoCache loadUserInfoFromDb(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return null;
        }
        boolean enabled = user.getStatus() == null || user.getStatus() != 0;
        Integer role = user.getRole();
        Long shopId = null;
        if (role != null && role == UserRole.MERCHANT.getCode()) {
            shopId = shopService.getShopIdByUserId(userId);
        }
        UserInfoCache info = new UserInfoCache(enabled, role, shopId);
        // 回填缓存，后续请求命中缓存
        try {
            redisUtils.set(USER_INFO_CACHE_PREFIX + userId, info, 7L * 24 * 60);
        } catch (Exception e) {
            log.warn("用户缓存回填失败: userId={}, error={}", userId, e.getMessage());
        }
        return info;
    }

    /** 从 claims 中安全提取 Long 值，使用 Java 16+ 模式匹配消除冗余强转 */
    private Long extractLong(Map<String, Object> claims, String key) {
        Object obj = claims.get(key);
        return obj instanceof Number number ? number.longValue() : null;
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

    /**
     * 写入 401 JSON 格式的错误响应。
     *
     * @param response HTTP 响应
     * @param msg      业务提示信息
     */
    private void writeError(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.error(HttpServletResponse.SC_UNAUTHORIZED, msg)));
    }
}
