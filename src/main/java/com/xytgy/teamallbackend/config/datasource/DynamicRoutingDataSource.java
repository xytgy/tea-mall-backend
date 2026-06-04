package com.xytgy.teamallbackend.config.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 动态数据源路由：根据 ThreadLocal 中的数据源类型选择主库或从库。
 * <p>
 * Spring 执行 SQL 前会调用 determineCurrentLookupKey()，
 * 返回 WRITE 或 READ，由 AbstractRoutingDataSource 切换到对应的数据源。
 */
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DynamicDataSourceContextHolder.get();
    }
}
