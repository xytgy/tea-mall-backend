package com.xytgy.teamallbackend.config;

import org.redisson.config.Config;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonAutoConfigurationCustomizer redissonCustomizer(
            @Value("${spring.data.redis.password:}") String redisPassword) {
        return config -> {
            if (redisPassword == null || redisPassword.isBlank()) {
                config.useSingleServer().setPassword(null);
            }
        };
    }
}
