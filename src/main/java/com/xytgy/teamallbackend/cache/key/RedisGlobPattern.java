package com.xytgy.teamallbackend.cache.key;

import java.util.regex.Pattern;

/**
 * 将 Redis Glob 规则转换为 Java 正则表达式。
 */
public final class RedisGlobPattern {

    public static final int MAX_PATTERN_LENGTH = 256;

    private RedisGlobPattern() {
    }

    public static Pattern compile(String glob) {
        validate(glob);
        StringBuilder regex = new StringBuilder("\\A");
        for (int i = 0; i < glob.length(); i++) {
            char current = glob.charAt(i);
            switch (current) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '[' -> i = appendCharacterClass(glob, i, regex);
                case '\\' -> {
                    if (i + 1 < glob.length()) {
                        regex.append(Pattern.quote(String.valueOf(glob.charAt(++i))));
                    } else {
                        regex.append(Pattern.quote("\\"));
                    }
                }
                default -> regex.append(Pattern.quote(String.valueOf(current)));
            }
        }
        return Pattern.compile(regex.append("\\z").toString());
    }

    public static void validate(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("缓存匹配规则不能为空");
        }
        if ("*".equals(pattern)) {
            throw new IllegalArgumentException("禁止使用 * 删除全部缓存");
        }
        if (pattern.length() > MAX_PATTERN_LENGTH) {
            throw new IllegalArgumentException(
                    "缓存匹配规则长度不能超过 " + MAX_PATTERN_LENGTH);
        }
    }

    private static int appendCharacterClass(String glob, int start, StringBuilder regex) {
        int end = glob.indexOf(']', start + 1);
        if (end < 0) {
            regex.append(Pattern.quote("["));
            return start;
        }

        String content = glob.substring(start + 1, end);
        if (content.isEmpty()) {
            regex.append(Pattern.quote("[]"));
            return end;
        }

        regex.append('[');
        int index = 0;
        char first = content.charAt(0);
        if (first == '^' || first == '!') {
            regex.append('^');
            index++;
        }
        for (; index < content.length(); index++) {
            char value = content.charAt(index);
            if (value == '\\' || value == ']') {
                regex.append('\\');
            }
            regex.append(value);
        }
        regex.append(']');
        return end;
    }
}
