package com.xytgy.teamallbackend.utils;

import io.jsonwebtoken.Jwts;
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

    @Value("${jwt.access-token-expiration-ms}")
    private Long accessTokenExpiration;
    private SecretKey key;
    @PostConstruct
    public void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) throw new IllegalStateException("Secret 至少需要32字节");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    public String createAccessToken(Map<String, Object> claims) {
        if (claims == null || claims.isEmpty()) {
            throw new IllegalArgumentException("claims 不能为空");
        }
        // 确保有用户ID
        if (!claims.containsKey("id")) {
            throw new IllegalArgumentException("claims 必须包含 id");
        }
        return Jwts.builder()
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .setClaims(claims)
                .signWith(key)
                .compact();
    }

    public Map<String, Object> parseToken (String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token).getBody();
    }
}


