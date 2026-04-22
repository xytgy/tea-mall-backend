package com.xytgy.teamallbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SPA (Single Page Application) 路由回退配置
 * 用于解决前端 Vue Router History 模式下刷新页面 404 的问题
 */
@Configuration
public class SpaRedirectConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将所有未匹配到具体文件的请求，都指向 static 目录
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        
                        // 1. 如果请求的资源存在且可读，直接返回该资源 (例如 js, css, 图片等静态文件)
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }
                        
                        // 2. 如果请求的是后端 API 接口或 Swagger/Knife4j 资源，不进行前端路由拦截
                        if (resourcePath.startsWith("api/") || 
                            resourcePath.startsWith("v3/api-docs") || 
                            resourcePath.startsWith("swagger-ui") || 
                            resourcePath.startsWith("doc.html") ||
                            resourcePath.startsWith("webjars")) {
                            return null;
                        }
                        
                        // 3. 如果请求的资源不存在，且不是 api 请求，则回退到 index.html 交给前端路由处理
                        return location.createRelative("index.html");
                    }
                });
    }
}
