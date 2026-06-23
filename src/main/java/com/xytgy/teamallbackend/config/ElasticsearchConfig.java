package com.xytgy.teamallbackend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch 配置类。
 * <p>
 * 启用 Spring Data Elasticsearch 仓库扫描，
 * 使 product.repository 包下的 Repository 接口自动注册为 Bean。
 * <p>
 * ES 客户端连接信息通过 application.yaml 中 spring.elasticsearch.* 属性自动配置，
 * 无需手动创建 ElasticsearchClient Bean。
 */
@Configuration
@EnableElasticsearchRepositories(basePackages = "com.xytgy.teamallbackend.module.product.repository")
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchConfig {
}
