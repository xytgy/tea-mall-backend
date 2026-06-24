package com.xytgy.teamallbackend.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.config.Config;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@AutoConfigureBefore(RedissonAutoConfiguration.class)
@ConditionalOnClass(RedissonAutoConfigurationCustomizer.class)
public class RedissonConfig {

    @Bean
    @ConditionalOnMissingBean(RedissonAutoConfigurationCustomizer.class)
    public RedissonAutoConfigurationCustomizer emptyPasswordFixer(RedisProperties redisProperties) {
        boolean passwordMissing = redisProperties.getPassword() == null
                || redisProperties.getPassword().isEmpty();

        return config -> {
            if (!passwordMissing) {
                return;
            }
            log.warn("Redis 密码为空，已跳过 AUTH 认证。"
                    + "生产环境请通过环境变量 REDIS_PASSWORD 设置强密码");
            applyToAllDeploymentModes(config);
        };
    }

    private void applyToAllDeploymentModes(Config config) {
        if (config.isSingleConfig()) {
            config.useSingleServer().setPassword(null);
        }
        if (config.isClusterConfig()) {
            config.useClusterServers().setPassword(null);
        }
        if (config.isSentinelConfig()) {
            config.useSentinelServers().setPassword(null);
        }
    }
}
