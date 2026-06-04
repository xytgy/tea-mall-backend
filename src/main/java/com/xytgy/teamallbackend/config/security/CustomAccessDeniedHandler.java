package com.xytgy.teamallbackend.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 权限不足时的统一响应处理器。
 * <p>
 * 当已登录用户访问 @PreAuthorize 校验不通过的资源时，返回 403 JSON 响应。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        log.debug("权限不足: uri={}, message={}",
                request.getRequestURI(), accessDeniedException.getMessage());

        response.getWriter().write(objectMapper.writeValueAsString(
                Result.error(ResultCode.FORBIDDEN)));
    }
}
