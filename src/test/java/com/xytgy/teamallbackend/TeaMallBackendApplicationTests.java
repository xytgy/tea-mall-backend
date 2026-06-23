package com.xytgy.teamallbackend;

import com.xytgy.teamallbackend.properties.CacheProperties;
import com.xytgy.teamallbackend.properties.CorsProperties;
import com.xytgy.teamallbackend.properties.DocsProperties;
import com.xytgy.teamallbackend.properties.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringJUnitConfig(TeaMallBackendApplicationTests.TestConfiguration.class)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-unit-tests-only-32bytes!",
        "jwt.access-token-expiration-ms=1800000",
        "jwt.refresh-token-expiration-ms=604800000",
        "cache.local.max-size=512",
        "cache.local.expire-seconds=60",
        "cors.allowed-origins=http://localhost:5173",
        "docs.enabled=false"
})
class TeaMallBackendApplicationTests {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private CacheProperties cacheProperties;

    @Autowired
    private CorsProperties corsProperties;

    @Autowired
    private DocsProperties docsProperties;

    @Test
    void contextLoads() {
        assertNotNull(jwtProperties);
        assertEquals(1_800_000L, jwtProperties.getAccessTokenExpirationMs());
        assertEquals(512L, cacheProperties.getLocal().getMaxSize());
        assertEquals("http://localhost:5173", corsProperties.getAllowedOrigins());
        assertFalse(docsProperties.isEnabled());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            JwtProperties.class,
            CacheProperties.class,
            CorsProperties.class,
            DocsProperties.class
    })
    static class TestConfiguration {
    }
}
