# 商品详情真实 Last-Modified 设计

## 目标

修复浏览器缓存层把“当前请求时间”误当作资源最后修改时间的问题。

本次只为商品详情接口 `GET /api/product/{id}` 接入真实的
`Last-Modified / If-Modified-Since` 协商缓存。缓存过期后，服务端先执行一次
轻量更新时间查询；如果商品没有变化，直接返回 `304 Not Modified`，不再执行
完整的商品详情查询。

## 范围

本次包含：

- 扩展 `@BrowserCache`，允许接口声明一个真实更新时间提供器。
- 新增通用的 `LastModifiedProvider` 扩展接口。
- 新增商品详情专用的 `ProductLastModifiedProvider`。
- 给 `ProductController.detail()` 添加商品详情缓存策略。
- 修正 `BrowserCacheAspect` 的 Last-Modified 判断。
- 关闭 `CacheHeaderFilter` 对动态 API 使用当前时间进行 Last-Modified 判断的行为。
- 增加对应的单元测试和回归测试。

本次不为商品列表、分类、轮播图、店铺等其他接口接入真实更新时间。

## 组件设计

### LastModifiedProvider

新增统一扩展接口：

```java
public interface LastModifiedProvider {
    Optional<Instant> getLastModified(Method method, Object[] args);
}
```

职责：

- 根据当前 Controller 方法和调用参数定位目标资源。
- 返回资源真实的最后更新时间。
- 无法确定更新时间时返回空。

Provider 只负责获得时间，不负责设置 HTTP 响应头或决定返回状态码。

### ProductLastModifiedProvider

商品详情 Provider 从 Controller 参数中取得商品 ID，并通过商品 Mapper 执行轻量查询：

```sql
SELECT update_time
FROM product
WHERE id = ?
  AND is_deleted = 0
LIMIT 1
```

如果现有表结构或逻辑删除配置已经由 MyBatis-Plus 自动处理，则使用项目现有查询
方式生成等价 SQL，避免重复维护删除条件。

查询结果处理：

- 商品存在且更新时间非空：转换为 `Instant` 后返回。
- 商品不存在或更新时间为空：返回空。
- 查询异常：向上抛出，由切面捕获并降级。

时间转换统一使用项目业务时区 `Asia/Shanghai`，最终 HTTP 日期仍以 GMT 输出。

### BrowserCache 注解

`@BrowserCache` 新增可选属性：

```java
Class<? extends LastModifiedProvider> lastModifiedProvider()
        default NoLastModifiedProvider.class;
```

默认实现表示未配置真实更新时间。未配置 Provider 的接口不生成动态
`Last-Modified`，也不执行 `If-Modified-Since` 判断。

商品详情使用：

```java
@BrowserCache(
    strategy = CacheStrategy.PRODUCT_DETAIL,
    lastModifiedProvider = ProductLastModifiedProvider.class
)
```

## 请求流程

### 首次请求

1. 切面读取商品详情方法上的 `@BrowserCache`。
2. 从 Spring 容器取得 `ProductLastModifiedProvider`。
3. Provider 根据商品 ID 查询真实 `update_time`。
4. 切面设置真实的 `Last-Modified`。
5. 执行 Controller 和商品详情业务查询。
6. 根据响应内容生成 ETag。
7. 返回 `200`、商品数据、`Cache-Control`、`Last-Modified` 和 `ETag`。

### 协商请求

浏览器携带：

```http
If-Modified-Since: <上次商品更新时间>
```

切面查询商品当前更新时间，然后按秒比较：

```text
商品当前更新时间 <= 浏览器记录时间
```

判断结果：

- 条件成立：返回 `304`，不执行 Controller。
- 商品更新时间更晚：执行 Controller，返回 `200` 和新内容。
- 商品不存在：继续执行 Controller，由现有业务逻辑返回原有错误。
- Provider 查询异常：记录警告并继续执行 Controller。

如果请求同时携带 `If-None-Match`，Last-Modified 未命中后仍执行原有 ETag
校验。ETag 作为内容级的最终校验手段保留。

## Filter 修正

`CacheHeaderFilter` 当前也使用请求时间作为动态 API 的最后修改时间，该语义不正确。

本次调整为：

- 动态 API 不生成基于当前时间的 `Last-Modified`。
- 动态 API 保留现有 `Cache-Control` 和 ETag。
- 静态资源可继续使用现有缓存策略；若没有可靠文件更新时间，则不进行提前
  `If-Modified-Since` 返回。

这样可以避免过滤器继续产生错误的 `Last-Modified` 或无效的 `304`。

## 异常与降级

- Provider 未配置：跳过 Last-Modified，继续执行 Controller 和 ETag。
- Provider 返回空：跳过 Last-Modified，继续原业务流程。
- Provider 抛出异常：记录警告，继续原业务流程和 ETag。
- `If-Modified-Since` 格式非法：忽略该请求头，继续原业务流程。
- 响应序列化或 ETag 生成失败：保持现有行为，返回正常业务响应。

缓存层不能把 Provider 故障转化为商品接口故障。

## 测试设计

需要覆盖：

- 商品详情首次请求使用真实更新时间设置 `Last-Modified`。
- 浏览器时间等于商品更新时间时返回 `304`，且 Controller 不执行。
- 商品更新时间晚于浏览器时间时执行 Controller。
- 商品不存在时继续原 Controller 流程。
- Provider 查询异常时降级执行 Controller 和 ETag。
- 未配置 Provider 的注解不生成虚假的 `Last-Modified`。
- 非法 `If-Modified-Since` 不影响正常响应。
- `CacheHeaderFilter` 不再为动态 API 使用当前时间生成 `Last-Modified`。
- 所有现有测试继续通过。

## 验收标准

1. 商品详情响应中的 `Last-Modified` 来自商品真实 `update_time`。
2. 商品未变化时，协商请求返回 `304`，且不执行完整商品详情查询。
3. 商品变化后返回 `200` 和新内容。
4. 商品不存在或 Provider 异常时，原业务行为不受影响。
5. 其他未接入 Provider 的动态接口不再生成虚假的 `Last-Modified`。
6. `./mvnw test` 全部通过。

## 不在本次范围内

- 商品列表的全局或筛选条件更新时间
- 分类、轮播图和店铺的真实 Last-Modified
- 数据库触发器或额外缓存版本表
- 修改 ETag 的生成算法
- 修改强缓存有效期
