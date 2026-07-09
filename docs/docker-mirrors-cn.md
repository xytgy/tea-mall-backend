# 国内可用 Docker 镜像源汇总

> 测试日期：2026-07-09  
> 测试环境：macOS (Apple Silicon)

---

## 一、可用镜像加速器（配置到 daemon.json）

| 镜像源 | 地址 | 状态 | 备注 |
|--------|------|------|------|
| docker.1ms.run | `https://docker.1ms.run` | ✅ 可用 | 国内代理，速度快 |
| docker.xuanyuan.me | `https://docker.xuanyuan.me` | ✅ 可用 | 国内代理 |
| DaoCloud | `https://docker.m.daocloud.io` | ✅ 可用 | DaoCloud 镜像加速 |
| hub.uuuadc.top | `https://hub.uuuadc.top` | ✅ 可用 | 社区镜像 |
| 南京大学 | `https://docker.nju.edu.cn` | ⚠️ 有限制 | 教育网，可能限速 |
| docker.anyhub.us.kg | `https://docker.anyhub.us.kg` | ❌ 不通 | - |
| dockerhub.icu | `https://dockerhub.icu` | ❌ 不通 | - |
| docker.rainbond.cc | `https://docker.rainbond.cc` | ❌ 不通 | - |

### 推荐 daemon.json 配置

```json
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me",
    "https://docker.m.daocloud.io"
  ]
}
```

---

## 二、华为云 SWR 镜像仓库（直接拉取，无需配置 daemon.json）

格式：`swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/<原始镜像名>`

| 原始镜像 | 华为云地址 | 已验证 |
|---------|-----------|--------|
| nacos/nacos-server:v2.3.2 | `swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/nacos/nacos-server:v2.3.2` | ✅ |
| apache/rocketmq:5.3.0 | `swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/apache/rocketmq:5.3.0` | ✅ |
| prom/prometheus:latest | `swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/prom/prometheus:latest` | ✅ |
| openzipkin/zipkin:latest | `swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/openzipkin/zipkin:latest` | ✅ |
| grafana/grafana:11.0.0 | `swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/grafana/grafana:11.0.0` | ✅ |
| grafana/loki:3.0.0 | `swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/grafana/loki:3.0.0` | ✅ |
| grafana/promtail:3.0.0 | `swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/grafana/promtail:3.0.0` | ✅ |
| elasticsearch:8.x | `swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/elasticsearch/elasticsearch:8.x` | ✅ |
| bitnami/kafka:3.7 | `swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/bitnami/kafka:3.7` | ✅ |

### 使用方式

```bash
# 1. 从华为云拉取
docker pull swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/grafana/loki:3.0.0

# 2. 打标签
docker tag swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/grafana/loki:3.0.0 grafana/loki:3.0.0

# 3. 用简化名称启动
docker run -d --name loki grafana/loki:3.0.0
```

---

## 三、docker.1ms.run 直接拉取

格式：`docker.1ms.run/<原始镜像名>`

| 原始镜像 | 代理地址 | 已验证 |
|---------|---------|--------|
| apache/skywalking-oap-server:8.9.0 | `docker.1ms.run/apache/skywalking-oap-server:8.9.0` | ✅ |
| apache/skywalking-ui:8.9.0 | `docker.1ms.run/apache/skywalking-ui:8.9.0` | ✅ |
| bladex/sentinel-dashboard:1.8.6 | `docker.1ms.run/bladex/sentinel-dashboard:1.8.6` | ✅ |
| apache/canal-server:v1.1.7 | `docker.1ms.run/apache/canal-server:v1.1.7` | ✅ |
| grafana/loki:3.0.0 | `docker.1ms.run/grafana/loki:3.0.0` | ✅ |

---

## 四、快速选择指南

| 场景 | 推荐方式 |
|------|---------|
| 配置 daemon.json 全局加速 | `docker.1ms.run` + `docker.xuanyuan.me` + `docker.m.daocloud.io` |
| 拉取特定镜像（最稳） | 华为云 SWR 地址 |
| 拉取特定镜像（备选） | `docker.1ms.run/` 前缀 |
| 临时拉取，不改配置 | 华为云 SWR 或 docker.1ms.run 前缀 |
