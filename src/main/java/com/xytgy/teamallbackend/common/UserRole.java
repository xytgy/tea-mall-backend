package com.xytgy.teamallbackend.common;

import lombok.Getter;

@Getter
public enum UserRole {
    USER(0),
    MERCHANT(1),
    ADMIN(2);

    private final int code;

    UserRole(int code) {
        this.code = code;
    }
}
