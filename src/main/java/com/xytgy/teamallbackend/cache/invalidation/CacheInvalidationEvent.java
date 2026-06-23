package com.xytgy.teamallbackend.cache.invalidation;

public record CacheInvalidationEvent(Operation operation, String target) {

    public enum Operation {
        KEY,
        PATTERN,
        VERSION
    }
}
