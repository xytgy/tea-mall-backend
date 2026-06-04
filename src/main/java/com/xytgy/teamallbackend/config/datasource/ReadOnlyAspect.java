package com.xytgy.teamallbackend.config.datasource;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 拦截 @ReadOnly 标注的方法，将数据源切换为从库。
 * @Order(-1) 确保在 @Transactional 之前执行，这样事务绑定的是正确的数据源。
 */
@Slf4j
@Aspect
@Component
@Order(-1)
public class ReadOnlyAspect {

    @Around("@annotation(com.xytgy.teamallbackend.config.datasource.ReadOnly) || " +
            "@within(com.xytgy.teamallbackend.config.datasource.ReadOnly)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        DataSourceType previous = DynamicDataSourceContextHolder.get();
        DynamicDataSourceContextHolder.set(DataSourceType.READ);
        try {
            return joinPoint.proceed();
        } finally {
            DynamicDataSourceContextHolder.set(previous);
        }
    }
}
