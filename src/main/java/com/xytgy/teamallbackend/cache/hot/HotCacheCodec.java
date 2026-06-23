package com.xytgy.teamallbackend.cache.hot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 热点缓存 JSON 编解码边界。
 */
class HotCacheCodec {
    private final ObjectMapper objectMapper;

    HotCacheCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    <T> String encode(T value, HotCacheOptions options) throws JsonProcessingException {
        long now = System.currentTimeMillis();
        boolean nullValue = value == null;
        long logicalExpireAt = now + (nullValue ? options.getNullTtl() : options.getLogicalTtl()).toMillis();
        HotCacheRecord<T> record = new HotCacheRecord<>(value, nullValue, logicalExpireAt, now);
        return objectMapper.writeValueAsString(record);
    }

    <T> HotCacheRecord<T> decode(String json, Class<T> type) throws JsonProcessingException {
        JavaType javaType = objectMapper.getTypeFactory()
                .constructParametricType(HotCacheRecord.class, type);
        try {
            return objectMapper.readValue(json, javaType);
        } catch (JsonProcessingException ignored) {
            HotCacheEntry<T> legacy = objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructParametricType(HotCacheEntry.class, type));
            return new HotCacheRecord<>(legacy.getData(), legacy.getData() == null,
                    legacy.getExpireAt(), 0L);
        }
    }

    <T> HotCacheRecord<T> decode(String json) throws JsonProcessingException {
        try {
            return objectMapper.readValue(json, new TypeReference<HotCacheRecord<T>>() {});
        } catch (JsonProcessingException ignored) {
            HotCacheEntry<T> legacy = objectMapper.readValue(json, new TypeReference<HotCacheEntry<T>>() {});
            return new HotCacheRecord<>(legacy.getData(), legacy.getData() == null,
                    legacy.getExpireAt(), 0L);
        }
    }
}
