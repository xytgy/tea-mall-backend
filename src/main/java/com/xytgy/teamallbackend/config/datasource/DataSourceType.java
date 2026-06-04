package com.xytgy.teamallbackend.config.datasource;

/**
 * 数据源类型：写（主库）/ 读（从库）。
 * 通过 ThreadLocal 传递，AOP 切面根据 @ReadOnly 注解自动切换。
 */
public enum DataSourceType {
    WRITE,
    READ
}
