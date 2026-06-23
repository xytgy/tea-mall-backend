package com.xytgy.teamallbackend.cache.bloom;

public record BloomFilterSyncMessage(EntityType type, Long id) {

    public enum EntityType {
        PRODUCT,
        USER
    }
}
