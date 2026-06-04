package com.xytgy.teamallbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // CORS 已迁移至 SecurityConfig.corsConfigurationSource()

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Knife4j / Swagger 静态资源映射
        registry.addResourceHandler("doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");

        // SPA 路由回退：未匹配到具体文件的请求指向 static 目录，交由前端路由处理
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);

                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }

                        // 后端 API 和 Knife4j 资源不做前端路由拦截
                        if (resourcePath.startsWith("api/") ||
                            resourcePath.startsWith("v3/api-docs") ||
                            resourcePath.startsWith("swagger-ui") ||
                            resourcePath.startsWith("doc.html") ||
                            resourcePath.startsWith("webjars")) {
                            return null;
                        }

                        // 静态资源不存在时回退到 index.html，交给前端路由处理
                        return location.createRelative("index.html");
                    }
                });
    }
}
