package com.xytgy.teamallbackend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/user/refresh/token",
                        "/api/product/list",
                        "/api/product/list/**", // 兼容带参数或后缀的情况
                        "/api/product/reviews",
                        "/api/store/**",
                        "/api/tea-circle/topics",
                        "/api/tea-circle/campaigns/latest",
                        "/api/feedback/submit",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/doc.html",
                        "/webjars/**",
                        "/uploads/**", // 排除图片静态资源路径拦截
                        "/ws/**" // 排除 WebSocket 路径拦截，让 WebSocketHandler 自己做鉴权
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置本地静态资源映射，将 /uploads/** 请求映射到本地绝对路径
        String uploadPath = new File("uploads/").getAbsolutePath() + File.separator;
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath);

        // 解决 Knife4j doc.html 404 问题
        registry.addResourceHandler("doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
