    package com.xytgy.teamallbackend.config.security;

    import com.fasterxml.jackson.databind.ObjectMapper;
    import jakarta.servlet.http.HttpServletRequest;
    import jakarta.servlet.http.HttpServletResponse;
    import org.springframework.http.MediaType;
    import org.springframework.security.core.AuthenticationException;
    import org.springframework.security.web.AuthenticationEntryPoint;
    import org.springframework.stereotype.Component;

    import java.io.IOException;
    import java.util.Map;

    /**
     * 未认证时的统一响应处理器。
     * <p>
     * 当请求未携带有效 Token 且访问受保护资源时，返回 401 JSON 响应。
     */
    @Component
    public class CustomAuthEntryPoint implements AuthenticationEntryPoint {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public void commence(HttpServletRequest request,
                             HttpServletResponse response,
                             AuthenticationException authException) throws IOException {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding("UTF-8");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            Map<String, Object> body = Map.of("code", 401, "message", "未提供登录凭证");
            response.getWriter().write(objectMapper.writeValueAsString(body));
        }
    }
