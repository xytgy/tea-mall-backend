# CloudTea 茶商城后端

A full-featured e-commerce backend for a tea marketplace, covering product trading, merchant management, social interaction (Tea Circle), instant messaging, and flash sales. Built as a comprehensive portfolio project demonstrating distributed system design and high-concurrency engineering.

---

## Architecture

```
+-----------------------------------------------------------+
                    API Layer (Controllers)
   Auth   Product   Order   FlashSale   Chat   Shop
+-----------------------------------------------------------+
                   Service Layer
  3-Tier Cache  Message Queue  Search   Distributed Lock
  (Caffeine      (RocketMQ)   (ES)       (Redisson)
   + Redis)
+-----------------------------------------------------------+
              Data Access (MyBatis-Plus + Flyway)
        MySQL Primary    MySQL Replica    H2 (Test)
+-----------------------------------------------------------+
         Infrastructure (Nacos / Docker / Zipkin)
+-----------------------------------------------------------+
```

---

## Highlights and Design Decisions

### 1. 3-Tier Cache (Caffeine L1 - Redis L2 - Browser L3)

**Why:** Product detail pages are read-heavy (99% reads). Without caching, a single MySQL instance at ~300 QPS becomes the bottleneck. Three tiers let the vast majority of requests resolve at Caffeine (microsecond latency), falling back to Redis only for cross-instance misses, and finally to DB.

**Beyond @Cacheable:** Spring's caching abstraction does not support custom fallback strategies or proactive hot-key preloading. This project's HotCacheCoordinator implements pluggable lock contention (preventing stampede on cache miss), and BloomFilterManager uses fail-open initialization - returning "possibly exists" - so an empty filter never falsely blocks real data during startup.

**Related code:** HotCacheService.java, BloomFilterManager.java

### 2. Flash Sale System (5-Layer Defense)

*Preheating - Captcha Ready - Rate Limit - Lua Atomic Deduct - MQ Async Order - Periodic Reconciliation - Circuit Breaker*

- Captcha pool (Redis pre-generation): Divides bot traffic
- Token bucket (Lua sliding window): Per-user and per-IP rate limiting
- Stock deduct (Redis Lua atomic script): GET + DECR + SISMEMBER in one atomic unit - eliminates lock contention
- Async order (RocketMQ transactional message): Stock deduction does not equal order creation; MQ guarantees eventual consistency
- Reconciliation (Scheduled reconciler): Compensates for lost MQ messages and DB write failures
- Circuit breaker (Resilience4j): Degrades to MySQL stock check when Redis fails continuously

**Lua vs. Distributed Lock:** Lua scripts execute atomically in Redis single-threaded model, achieving about 5-10x higher throughput than Redisson lock-based stock deduction. The trade-off - increased script complexity - is acceptable here given the simple deduct logic.

**Related code:** FlashSaleCoreService.java, flash_deduct.lua

### 3. Distributed Rate Limiting

Redis Sorted Set sliding window, dual dimensions (account + IP), hourly auto-lockout. Redis down leads to fail-open (site stays available).

Simple fixed-window (INCR + EXPIRE) has edge-boundary spikes. This implementation uses a Sorted Set to record each request timestamp, giving millisecond-precision windowing. The dual-dimension (account + IP) approach mitigates credential-stuffing attacks without a dedicated WAF.

**Related code:** RateLimitService.java, sliding_window_limit.lua

### 4. Bloom Filter for Cache Penetration Prevention

Guava BloomFilter (JVM local) with periodic full rebuild from DB. Fail-open on init, configurable false-positive rate, incremental insertion.

In e-commerce, a long tail of cold products remains uncached. Bloom Filters reject invalid IDs at about 1 MB per 10 million entries, preventing cache-miss storms to the database.

**Design trade-off:** A JVM-local Bloom Filter is fast but needs cross-instance synchronization. This project uses a hybrid approach - periodic full rebuild from DB plus Redis channel for incremental new-ID notifications - keeping instances eventually consistent without a shared filter service.

**Related code:** BloomFilterManager.java, BloomFilterRebuildService.java

### 5. Async Messaging and Eventual Consistency

- Flash sale: Lua deduct, RocketMQ async order creation, payment callback, shipping notification
- Order timeout: RocketMQ delayed message (30 min), auto-cancel, stock replenish

RocketMQ was chosen for its mature transactional and delayed message support. In the flash sale path, deducting stock takes milliseconds but creating an order (with inventory reservation, coupon deduction, and points update) can take hundreds of milliseconds. MQ async brings the flash sale API response time down to a stable ~10ms.

**Related code:** FlashOrderPublisher.java

---

## Tech Stack

- Language: Java 17 (Records, Pattern Matching)
- Framework: Spring Boot 3.3.4, Spring Cloud Alibaba
- ORM: MyBatis-Plus 3.5.10, Flyway
- Database: MySQL 8.0 (read/write split), H2 (test)
- Cache: Redis 7 plus Caffeine
- MQ: RocketMQ 5.3
- Search: Elasticsearch 8.13 (IK analyzer)
- Config: Nacos 2.3.2 (service discovery and config center)
- Distributed Lock: Redisson
- Circuit Breaker: Resilience4j
- Tracing: Zipkin and Micrometer
- Security: Spring Security and JWT (dual token)
- Payment: Alipay SDK
- API Docs: Knife4j (OpenAPI 3)
- Deployment: Docker Compose

---

## Modules

13 business modules, decoupled by ID references (no cross-module JOIN coupling - future microservice-ready):

- User and Auth: Registration, login (JWT), RBAC, profiles, addresses
- Product: SPU/SKU, categories, brands, reviews, ES full-text search
- Shop: Merchant onboarding, shop management, follows
- Cart: Add/remove/select, real-time price calculation
- Order: Creation, payment (Alipay + mock), receipt, cancellation, refund, review
- Flash Sale: Captcha, rate limit, Lua atomic deduct, MQ async, reconciliation
- Tea Circle: Posts, threaded comments, likes, follows, topics, notifications
- Chat: WebSocket buyer-merchant messaging, Redis Pub/Sub multi-instance broadcast
- Favorite: Product wishlist
- Feedback: User feedback with optional images
- Support: Customer service tickets
- Upload: Aliyun OSS file upload with format validation
- Banner: Homepage carousel management

---

## Quick Start

```bash
# Prerequisites: Java 17+, Docker, Maven

# 1. Start middleware (MySQL + Redis)
docker compose -f docker-compose.yml up -d

# 2. Run (dev profile disables Nacos/ES/Zipkin - only needs MySQL + Redis)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Open API docs
# http://localhost:8080/doc.html
```

## Tests

```bash
./mvnw test
```

Covers Bloom Filter lifecycle (init/rebuild/sync), cache invalidation events, hot cache lock, flash sale flow integration (MQ to DB), payment callback, rate limiting, and browser cache headers.

## Deployment

```bash
docker compose -f docker-compose.prodlike.yml up -d
# Environment variables via .env.prodlike
```

Production stack: 3-node Nacos cluster, MySQL, Redis, RocketMQ (broker + namesrv), Zipkin.

---

> Built with Java 17 + Spring Boot 3.3.4.
