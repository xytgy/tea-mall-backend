# Cache 包结构整理设计

## 目标

将当前平铺在 `cache` 包中的文件按职责分组，降低查找和理解成本，不改变任何缓存行为、公开方法或 Spring Bean 名称。

## 结构

```text
cache
├── bloom
│   ├── BloomFilterManager
│   ├── BloomFilterRebuildService
│   ├── BloomFilterSyncMessage
│   ├── BloomFilterSyncService
│   └── event
├── browser
│   ├── LastModifiedProvider
│   └── NoLastModifiedProvider
└── multilevel
    ├── CacheKeyCleaner
    ├── CacheMetrics
    ├── HotCacheEntry
    ├── HotCacheService
    └── MultiLevelCacheService
```

## 约束

- 仅移动文件并修改 `package`、`import`。
- 不修改方法签名、缓存流程、配置和运行逻辑。
- 对应测试移动到相同模块包。
- 整理后运行完整测试。

