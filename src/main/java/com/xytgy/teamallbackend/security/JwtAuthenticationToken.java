package com.xytgy.teamallbackend.security;

import lombok.Getter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * 自定义认证令牌，携带 JWT 解析出的完整用户信息。
 * <p>
 * 从 JwtAuthenticationFilter 中构建，写入 SecurityContextHolder 后，
 * 业务层可通过 {@link SecurityUtils} 获取 userId、role、shopId。
 */
@Getter
public class JwtAuthenticationToken extends UsernamePasswordAuthenticationToken {

    private final Long userId;
    private final Integer role;
    private final Long shopId;

    public JwtAuthenticationToken(Long userId, Integer role, Long shopId,
                                  Collection<? extends GrantedAuthority> authorities) {
        super(userId, null, authorities);
        this.userId = userId;
        this.role = role;
        this.shopId = shopId;
    }
}
