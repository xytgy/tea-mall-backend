package com.xytgy.teamallbackend.config.datasource;

import lombok.extern.slf4j.Slf4j;

/**
 * 动态数据源上下文：通过 ThreadLocal 在当前线程中保存数据源类型。
 * 默认走主库（WRITE），标注 @ReadOnly 的方法自动切换到从库（READ）。
 */
@Slf4j
public final class DynamicDataSourceContextHolder {

    private static final ThreadLocal<DataSourceType> CONTEXT = ThreadLocal.withInitial(() -> DataSourceType.WRITE);

    private DynamicDataSourceContextHolder() {
    }

    public static DataSourceType get() {
        return CONTEXT.get();
    }

    public static void set(DataSourceType type) {
        CONTEXT.set(type);
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
