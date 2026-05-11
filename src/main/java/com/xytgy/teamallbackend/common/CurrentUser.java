package com.xytgy.teamallbackend.common;

import lombok.Data;

import java.util.Map;

@Data
public class CurrentUser {
    private Long id;
    private Integer role;
    private Long shopId;

    public static CurrentUser fromClaims(Map<String, Object> claims) {
        if (claims == null) {
            return null;
        }
        CurrentUser user = new CurrentUser();
        Object idObj = claims.get("id");
        if (idObj != null) {
            user.setId(Long.valueOf(String.valueOf(idObj)));
        }
        Object roleObj = claims.get("role");
        if (roleObj != null) {
            user.setRole(Integer.valueOf(String.valueOf(roleObj)));
        }
        Object shopIdObj = claims.get("shopId");
        if (shopIdObj != null) {
            user.setShopId(Long.valueOf(String.valueOf(shopIdObj)));
        }
        return user;
    }
}

