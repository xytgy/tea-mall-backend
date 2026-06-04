package com.xytgy.teamallbackend.common;

import lombok.Getter;

@Getter
public enum UserRole {
    USER(0),
    MERCHANT(1),
    ADMIN(2);

    /** switch-case 可用的编译期常量（Java 要求 case 值为常量表达式） */
    public static final int CODE_USER = 0;
    public static final int CODE_MERCHANT = 1;
    public static final int CODE_ADMIN = 2;

    private final int code;

    UserRole(int code) {
        this.code = code;
    }
}
