package com.xytgy.teamallbackend.cache.invalidation;

public record CacheInvalidationMessage(
        Operation operation,
        String target,
        String sourceInstanceId,
        String eventId) {

    public enum Operation {
        KEY,
        PATTERN
    }
}
