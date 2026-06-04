package com.xytgy.teamallbackend.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.xytgy.teamallbackend.security.SecurityConstants;

import java.util.List;

/**
 * Spring Security 核心配置。
 * <p>
 * 职责：
 * 1. 关闭 CSRF（REST API 不需要）
 * 2. 设置 Session 策略为 STATELESS（完全依赖 JWT，不创建 HttpSession）
 * 3. 定义公开路径白名单与受保护路径
 * 4. 注入自定义 JWT 过滤器，替代原 JwtInterceptor
 * 5. 配置统一的 401/403 JSON 响应
 * 6. 启用 @PreAuthorize 方法级权限控制
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CustomAuthEntryPoint customAuthEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    @Value("${cors.allowed-origins:*}")
    private String[] allowedOrigins;

    /**
     * 是否开放 API 文档（开发环境为 true，生产环境应设置为 false）
     */
    @Value("${docs.enabled:true}")
    private boolean docsEnabled;



    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(customAuthEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(SecurityConstants.PUBLIC_PATHS).permitAll();
                    // 仅在 docs.enabled=true 时开放 API 文档访问
                    if (docsEnabled) {
                        auth.requestMatchers(SecurityConstants.DOCS_PATHS).permitAll();
                    }
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.anyRequest().authenticated();
                })
                // 将 JWT 过滤器插入到 UsernamePasswordAuthenticationFilter 之前
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 通配符 * 与 allowCredentials=true 互斥，防止任意来源携带凭证的跨域攻击
        boolean isWildcard = List.of(allowedOrigins).contains("*");

        for (String origin : allowedOrigins) {
            config.addAllowedOriginPattern(origin);
        }

        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(!isWildcard);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
