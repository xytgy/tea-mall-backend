package com.xytgy.teamallbackend.config;


import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String LOGIN_USER_KEY_PREFIX = "login:user:";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            try {
                // 对 AccessToken 进行解析
                Map<String, Object> claims = jwtUtils.parseToken(token);
                
                Long userId = Long.valueOf(claims.get("id").toString());

                // 1. 检查 Redis 中的在线状态
                if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(LOGIN_USER_KEY_PREFIX + userId))) {
                    writeUnauthorized(response, 401, "登录已失效，请重新登录");
                    return false;
                }

                // 2. 检查用户是否被禁用
                if (!userService.isUserEnabled(userId)) {
                    writeUnauthorized(response, 401, "您的账号状态异常或已被封禁，请重新登录");
                    return false;
                }

                // 存入用户信息，实现全局可访问 + 线程隔离
                UserContext.setUser(claims);
                return true;
            } catch (ExpiredJwtException e) {
                // AccessToken 过期，告知前端触发刷新 Token 逻辑
                writeUnauthorized(response, 10002, "TOKEN_EXPIRED");
                return false;
            } catch (Exception e) {
                writeUnauthorized(response, 401, "登录状态无效，请重新登录");
                return false;
            }
        }
        writeUnauthorized(response, 401, "未登录或令牌缺失");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContext.clear();
    }


    // 当用户未授权时，手动返回一个 JSON 响应给前端
    private void writeUnauthorized(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("message", msg);
        body.put("data", null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
