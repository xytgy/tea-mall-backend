package com.xytgy.teamallbackend.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.properties.RateLimitProperties;
import com.xytgy.teamallbackend.utils.RequestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * 全局 API 限流过滤器。
 * <p>
 * 使用令牌桶算法（Token Bucket）实现，基于 Redis Lua 脚本保证原子性。
 * 令牌桶算法允许突发流量（桶内积累的令牌），同时平滑限制长期请求速率。
 * </p>
 * <p>
 * 按客户端 IP 进行限流，每个 IP 维护独立的令牌桶。
 * </p>
 *
 * @see RateLimitProperties
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class GlobalRateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate stringRedisTemplate;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:global:";

    /**
     * 令牌桶 Lua 脚本。
     * <p>
     * KEYS[1] = 令牌桶 key
     * ARGV[1] = 桶容量（最大令牌数）
     * ARGV[2] = 每秒补充的令牌数
     * ARGV[3] = 当前时间戳（秒，支持小数）
     * <p>
     * 返回值：1 = 允许通过，0 = 被限流
     */
    private static final String TOKEN_BUCKET_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            local bucket = redis.call('hmget', key, 'tokens', 'last_refill')
            local tokens = tonumber(bucket[1])
            local last_refill = tonumber(bucket[2])

            if tokens == nil then
                -- 桶不存在，初始化为满桶
                tokens = capacity
                last_refill = now
            end

            -- 计算上次补充到现在应补充的令牌数
            local elapsed = now - last_refill
            local new_tokens = elapsed * rate
            tokens = math.min(capacity, tokens + new_tokens)
            last_refill = now

            if tokens >= 1 then
                -- 消耗一个令牌
                tokens = tokens - 1
                redis.call('hmset', key, 'tokens', tokens, 'last_refill', last_refill)
                redis.call('expire', key, math.ceil(capacity / rate) + 1)
                return 1
            else
                -- 令牌不足，仅更新时间戳（不消耗令牌）
                redis.call('hmset', key, 'tokens', tokens, 'last_refill', last_refill)
                redis.call('expire', key, math.ceil(capacity / rate) + 1)
                return 0
            end
            """;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        // 仅对 API 路径限流，放行健康检查、监控端点和静态资源
        return !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String clientIp = RequestUtils.getClientIp(request);
        String key = RATE_LIMIT_KEY_PREFIX + clientIp;

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(TOKEN_BUCKET_SCRIPT, Long.class);
        Long result = stringRedisTemplate.execute(
                redisScript,
                Collections.singletonList(key),
                String.valueOf(properties.getCapacity()),
                String.valueOf(properties.getRate()),
                String.valueOf(System.currentTimeMillis() / 1000.0));

        if (result != null && result == 1L) {
            // 令牌获取成功，放行请求
            filterChain.doFilter(request, response);
        } else {
            // 被限流，返回 429
            log.warn("全局限流触发, ip={}, uri={}", clientIp, request.getRequestURI());
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", "1");
            String body = objectMapper.writeValueAsString(
                    Result.error(ResultCode.TOO_MANY_REQUESTS));
            response.getWriter().write(body);
        }
    }

}
