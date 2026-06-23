package com.xytgy.teamallbackend.cache.key;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisGlobPatternTest {

    @Test
    void shouldSupportRedisGlobOperators() {
        assertTrue(RedisGlobPattern.compile("product:*").matcher("product:123").matches());
        assertTrue(RedisGlobPattern.compile("user:?").matcher("user:1").matches());
        assertTrue(RedisGlobPattern.compile("item:[ab]").matcher("item:a").matches());
        assertTrue(RedisGlobPattern.compile("item:[^ab]").matcher("item:c").matches());
        assertFalse(RedisGlobPattern.compile("item:[^ab]").matcher("item:a").matches());
    }

    @Test
    void shouldRejectDangerousPatterns() {
        assertThrows(IllegalArgumentException.class, () -> RedisGlobPattern.compile(null));
        assertThrows(IllegalArgumentException.class, () -> RedisGlobPattern.compile(" "));
        assertThrows(IllegalArgumentException.class, () -> RedisGlobPattern.compile("*"));
        assertThrows(IllegalArgumentException.class,
                () -> RedisGlobPattern.compile("x".repeat(257)));
    }
}
