package com.xytgy.teamallbackend.config;


import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
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
                //对token进行解码，并获取里面的存入的信息
                Map<String, Object> claims = jwtUtils.parseToken(token);
                
                // Redis 优先校验用户状态（未命中自动回源数据库并回填）
                Long userId = Long.valueOf(claims.get("id").toString());
                if (!userService.isUserEnabled(userId)) {
                    writeUnauthorized(response, "您的账号状态异常或已被封禁，请重新登录");
                    return false;
                }

                //存入用户信息，实现全局可访问 + 线程隔离
                UserContext.setUser(claims);
                return true;
            } catch (Exception e) {
                writeUnauthorized(response, "登录状态无效，请重新登录");
                return false;
            }
        }
        writeUnauthorized(response, "未登录或令牌缺失");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContext.clear();
    }


    //当用户未授权时，手动返回一个 401 的 JSON 响应给前端
    private void writeUnauthorized(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new HashMap<>();
        body.put("code", 401);
        body.put("message", msg);
        body.put("data", null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
