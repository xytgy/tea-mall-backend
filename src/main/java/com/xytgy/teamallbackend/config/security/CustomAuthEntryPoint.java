package com.xytgy.teamallbackend.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未认证时的统一响应处理器。
 * <p>
 * 当请求未携带有效 Token 且访问受保护资源时，返回 401 JSON 响应。
 * 根据异常类型给出具体的中文提示，便于前端展示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String message = resolveMessage(authException);
        log.debug("认证失败: uri={}, exception={}, message={}",
                request.getRequestURI(), authException.getClass().getSimpleName(), message);

        response.getWriter().write(objectMapper.writeValueAsString(
                Result.error(ResultCode.UNAUTHORIZED.getCode(), message)));
    }

    /**
     * 根据异常类型解析用户友好的提示信息。
     * 如果异常自身携带了有意义的消息则优先使用。
     */
    private String resolveMessage(AuthenticationException ex) {
        String raw = ex.getMessage();
        // 有自定义消息时直接使用（如 JwtAuthenticationFilter 中抛出的异常）
        if (raw != null && !raw.isBlank() && !isDefaultMessage(raw)) {
            return raw;
        }
        if (ex instanceof BadCredentialsException) {
            return "用户名或密码错误";
        }
        if (ex instanceof InsufficientAuthenticationException) {
            return "未提供登录凭证";
        }
        if (ex instanceof CredentialsExpiredException) {
            return "登录凭证已过期";
        }
        return "认证失败";
    }

    /** 判断是否为 Spring Security 默认生成的无意义消息 */
    private boolean isDefaultMessage(String msg) {
        return msg.equals("Bad credentials")
                || msg.equals("Insufficient authentication")
                || msg.equals("Full authentication is required to access this resource");
    }
}
