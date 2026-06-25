package com.xytgy.teamallbackend.ratelimit;

import com.xytgy.teamallbackend.properties.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import static com.xytgy.teamallbackend.ratelimit.RateLimitService.FLAG_LIMITED;
import static com.xytgy.teamallbackend.ratelimit.RateLimitService.FLAG_LOCKED;
import static com.xytgy.teamallbackend.ratelimit.RateLimitService.isAllowed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RateLimitProperties properties;
    private RateLimitService service;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        RateLimitProperties.Login login = properties.getLogin();
        login.setAccountMaxPerMinute(3);
        login.setAccountMaxPerHour(10);
        login.setAccountLockoutMinutes(15);
        login.setIpMaxPerMinute(5);
        login.setIpMaxPerHour(20);
        login.setIpLockoutMinutes(15);

        service = new RateLimitService(redisTemplate, properties, new RateLimitMetrics(null));
        ReflectionTestUtils.setField(service, "activeProfile", "test");
        service.init();
    }

    @Test
    void accountRateReturnsAllowedWhenUnderMinuteLimit() {
        String identifier = "user1";
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(2L);

        long result = service.checkAccountRate(identifier);

        assertThat(isAllowed(result)).isTrue();
        assertThat(result).isEqualTo(2L);
    }

    @Test
    void accountRateReturnsLimitedWhenOverMinuteLimit() {
        String identifier = "user2";
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(FLAG_LIMITED);

        long result = service.checkAccountRate(identifier);

        assertThat(isAllowed(result)).isFalse();
        assertThat(result).isEqualTo(FLAG_LIMITED);
    }

    @Test
    void accountRateReturnsLockedWhenOverHourLimit() {
        String identifier = "user3";
        // First call returns minute OK, second returns locked
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0L)     // minute window OK
                .thenReturn(FLAG_LOCKED); // hour window locked

        long result = service.checkAccountRate(identifier);

        assertThat(isAllowed(result)).isFalse();
        assertThat(isAllowed(result)).isFalse();
        assertThat(result).isEqualTo(FLAG_LOCKED);
    }

    @Test
    void resetAccountAttemptsClearsRedisKeys() {
        service.resetAccountAttempts("user4");

        verify(redisTemplate).execute(any(DefaultRedisScript.class), anyList());
    }

    @Test
    void ipRateReturnsAllowedWhenUnderLimit() {
        String ip = "192.168.1.1";
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(3L);

        long result = service.checkIpRate(ip);

        assertThat(isAllowed(result)).isTrue();
        assertThat(result).isEqualTo(3L);
    }

    @Test
    void ipRateReturnsLimitedWhenOverLimit() {
        String ip = "192.168.1.2";
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(FLAG_LIMITED);

        long result = service.checkIpRate(ip);

        assertThat(isAllowed(result)).isFalse();
        assertThat(isAllowed(result)).isFalse();
        assertThat(result).isEqualTo(FLAG_LIMITED);
    }

    @Test
    void failsOpenWhenRedisIsDown() {
        String identifier = "user5";
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis is down"));

        long result = service.checkAccountRate(identifier);

        assertThat(isAllowed(result)).isTrue();
        assertThat(result).isEqualTo(0L);
    }

    @Test
    void accountLockDetectionWorks() {
        String identifier = "user6";
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        boolean locked = service.isAccountLocked(identifier);

        assertThat(locked).isTrue();
    }

    @Test
    void ipLockDetectionWorks() {
        String ip = "192.168.1.3";
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        boolean locked = service.isIpLocked(ip);

        assertThat(locked).isFalse();
    }

    @Test
    void isAllowedReturnsFalseForLimitedAndLocked() {
        assertThat(isAllowed(FLAG_LIMITED)).isFalse();
        assertThat(isAllowed(FLAG_LOCKED)).isFalse();
    }

    @Test
    void isAllowedReturnsTrueForPositiveValues() {
        assertThat(isAllowed(0L)).isTrue();
        assertThat(isAllowed(5L)).isTrue();
    }
}
