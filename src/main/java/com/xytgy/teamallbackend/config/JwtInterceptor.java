package com.xytgy.teamallbackend.config;



import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.common.CurrentUser;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.utils.JwtUtils;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.http.HttpHeaders;

import java.security.SignatureException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private static final String LOGIN_USER_KEY_PREFIX = "login:user:";
    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, 401, "未提供登录凭证");
            return false;
        }

        String token = authHeader.substring(7);
        try {
            //解析token里携带的信息
            Map<String, Object> claim = jwtUtils.parseToken(token);
            Object userIdObj = claim.get("userId");
            if (userIdObj == null) {
                writeUnauthorized(response, 401, "Token 格式错误");
                return false;
            }
            Long userId = Long.valueOf(userIdObj.toString());

            Boolean hasKey = stringRedisTemplate.hasKey(LOGIN_USER_KEY_PREFIX + userId);
            if (Boolean.FALSE.equals(hasKey)) {
                writeUnauthorized(response, 401, "登录已失效，请重新登录");
                return false;
            }

            if (!userService.isUserEnabled(userId)) {
                writeUnauthorized(response, 401, "账号已禁用，请联系管理员");
                return false;
            }

            UserContext.setUser(CurrentUser.fromClaims(claim));
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("Token 已过期: {}", e.getMessage());
            writeUnauthorized(response, 10002, "TOKEN_EXPIRED");
            return false;
        } catch (MalformedJwtException | SignatureException e) {
            log.warn("Token 无效: {}", e.getMessage());
            writeUnauthorized(response, 401, "Token 无效");
            return false;
        } catch (Exception e) {
            log.error("JWT 验证失败", e);
            writeUnauthorized(response, 401, "登录状态验证失败");
            return false;
        }

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)  {
        UserContext.clear();
    }


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
