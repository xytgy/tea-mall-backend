package com.xytgy.teamallbackend.config;

import com.alibaba.cloud.nacos.refresh.NacosRefreshHistory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 修复 NacosRefreshHistory Bean 缺失问题。
 * Spring Cloud Alibaba 2023.0.1.0 + Spring Boot 3.3.4 的已知兼容性问题。
 */
@Configuration
public class NacosFixConfig {

    @Bean
    public NacosRefreshHistory nacosRefreshHistory() {
        return new NacosRefreshHistory();
    }
}
