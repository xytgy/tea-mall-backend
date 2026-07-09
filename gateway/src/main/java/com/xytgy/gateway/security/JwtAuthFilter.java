package com.xytgy.gateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String LOGIN_USER_KEY_PREFIX = "login:user:";

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public int getOrder() {
        return -100; // 高优先级，先执行
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 公开路径直接放行
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // 提取 token
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "未提供登录凭证");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            // 解析 JWT
            Map<String, Object> claims = jwtUtils.parseToken(token);
            Long userId = extractLong(claims, "id");
            if (userId == null) {
                return unauthorized(exchange, "Token 格式错误");
            }

            // 检查用户是否在线（Redis）
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(LOGIN_USER_KEY_PREFIX + userId))) {
                return unauthorized(exchange, "登录已失效，请重新登录");
            }

            // 放行，把用户信息传递给后端
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            log.warn("Token 解析失败: {}", e.getMessage());
            return unauthorized(exchange, "Token 无效");
        }
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/product/list")
                || path.startsWith("/api/product/search")
                || path.startsWith("/api/store/")
                || path.startsWith("/api/banner/")
                || path.startsWith("/api/category/")
                || path.startsWith("/api/tea-circle/topics")
                || path.startsWith("/api/feedback/")
                || path.startsWith("/actuator/")
                || path.startsWith("/doc.html")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/webjars/");
    }

    private Long extractLong(Map<String, Object> claims, String key) {
        Object obj = claims.get(key);
        return obj instanceof Number number ? number.longValue() : null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format("{\"success\":false,\"code\":401,\"message\":\"%s\"}", message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
