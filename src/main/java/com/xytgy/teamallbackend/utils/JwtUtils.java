package com.xytgy.teamallbackend.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration-ms:1800000}") // 默认 30 分钟
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration-ms:604800000}") // 默认 7 天
    private long refreshTokenExpiration;

    private SecretKey key;

    @PostConstruct
    public void init() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("jwt.secret 长度至少需要 32 个字符");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 创建 AccessToken (JWT)
     */
    public String createAccessToken(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Map<String, Object> parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
