package com.xytgy.teamallbackend.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordUtil {

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * 加密密码
     */
    public static String encrypt(String password) {
        return encoder.encode(password);
    }

    /**
     * 校验密码
     */
    public static boolean match(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}