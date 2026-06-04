package com.xytgy.teamallbackend.config.datasource;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 动态数据源配置：主库（写）+ 从库（读）。
 * <p>
 * 通过 spring.datasource.read-slave.enabled=true 开启读写分离，
 * 未开启时退化为单数据源（主库），对业务代码零影响。
 */
@Slf4j
@Configuration
public class DynamicDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource primaryDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${spring.datasource.driver-class-name}") String driverClassName) {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName(driverClassName)
                .url(url)
                .username(username)
                .password(password)
                .build();
        ds.setPoolName("Primary-Writer");
        // P2#27: 日志脱敏，避免 connection string 模式下泄露密码
        log.info("主库（写）数据源初始化: {}", url.replaceAll("password=[^&]*", "password=***"));
        return ds;
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.datasource.read-slave", name = "enabled", havingValue = "true")
    @ConfigurationProperties(prefix = "spring.datasource.read-slave.hikari")
    public HikariDataSource readSlaveDataSource(
            @Value("${spring.datasource.read-slave.url}") String url,
            @Value("${spring.datasource.read-slave.username}") String username,
            @Value("${spring.datasource.read-slave.password}") String password,
            @Value("${spring.datasource.read-slave.driver-class-name:com.mysql.cj.jdbc.Driver}") String driverClassName) {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName(driverClassName)
                .url(url)
                .username(username)
                .password(password)
                .build();
        ds.setPoolName("Read-Slave");
        log.info("从库（读）数据源初始化: {}", url.replaceAll("password=[^&]*", "password=***"));
        return ds;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "spring.datasource.read-slave", name = "enabled", havingValue = "true")
    public DataSource dynamicDataSource(
            HikariDataSource primaryDataSource,
            HikariDataSource readSlaveDataSource) {
        DynamicRoutingDataSource routing = new DynamicRoutingDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.WRITE, primaryDataSource);
        targetDataSources.put(DataSourceType.READ, readSlaveDataSource);

        routing.setTargetDataSources(targetDataSources);
        routing.setDefaultTargetDataSource(primaryDataSource);

        log.info("动态数据源路由初始化: 主库（写）+ 从库（读）");
        return routing;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "spring.datasource.read-slave", name = "enabled", havingValue = "false", matchIfMissing = true)
    public DataSource singleDataSource(HikariDataSource primaryDataSource) {
        log.info("单数据源模式（未开启读写分离）");
        return primaryDataSource;
    }
}
