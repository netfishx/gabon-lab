# Gabon 后端四语言对比评测报告

> 测试日期：2026-03-19（Kotlin 新增）/ 2026-03-15（Java/Go/Rust 基线）
> 测试环境：macOS Darwin 25.4.0, Apple Silicon, Docker (OrbStack)
> 数据库：PostgreSQL 18 (Docker) / MySQL 8.4 (Docker, Java only)
> 缓存：Redis 8 (Docker)
> 存储：Garage S3 v2.2.0 (Docker)

## 项目概述

四个独立实现同一短视频平台后端 API，用于语言/框架对比：

| | Java | Go | Rust | Kotlin |
|---|---|---|---|---|
| 框架 | Spring Boot 3.2.4 | Echo v4 + Huma v2 | Axum 0.8 | Ktor 3.4.0 + Netty |
| 数据库 | MyBatis-Plus + MySQL | pgx/v5 + sqlc | SQLx + PostgreSQL | Exposed 1.1.1 + PostgreSQL |
| 运行时 | JVM (Zulu 17) | Go 1.26 | Tokio (Rust 1.94) | JVM 21 (Virtual Threads + ZGC) |
| 缓存 | Jedis + Redisson | go-redis/v9 | deadpool-redis | Lettuce 7.5 (coroutines) |

## 1. 性能

### 1.1 吞吐量 (QPS)

测试工具：wrk (4 线程, 200 并发, 10 秒)

| 端点 | Java | Go | Rust | Kotlin |
|------|------|-----|------|--------|
| health (无 DB) | 88,149 | 100,079 | **210,400** | 99,431 |
| 相对倍数 | 1x | 1.14x | **2.39x** | 1.13x |

Kotlin (Ktor+Netty+VT) 几乎追平 Go，JIT 热路径优化有效。Rust 仍然遥遥领先。

### 1.2 延迟分布

测试工具：wrk --latency (4 线程, 200 并发, 10 秒)

| 百分位 | Go | Rust | Kotlin |
|--------|-----|------|--------|
| p50 | 1.79 ms | **0.70 ms** | 1.60 ms |
| p75 | 2.80 ms | **0.85 ms** | 2.31 ms |
| p90 | 3.91 ms | **1.01 ms** | 4.24 ms |
| p99 | 6.13 ms | **1.48 ms** | 15.36 ms |

- **Kotlin p50 优于 Go**（1.60 vs 1.79 ms），JIT 在热路径上的优化生效
- **Kotlin p99 是弱点**（15.36 ms），ZGC 暂停和 JIT 编译抖动导致尾延迟偏高
- **Rust 全百分位领先**，p99 仅 1.48 ms，尾延迟极其稳定

### 1.3 运行时内存 (RSS)

| 阶段 | Java | Go | Rust | Kotlin |
|------|------|-----|------|--------|
| 空闲 | 354 MB | 25 MB | **19 MB** | 254 MB |
| 200c 压测后 | 370 MB | 46 MB | **21 MB** | 638 MB |
| 500c 峰值 | 372 MB | 60 MB | **33 MB** | 650 MB |

- Kotlin 空闲 254 MB（Go 的 10 倍），主要是 JVM 堆预分配 + Netty buffer pool
- Kotlin 压测后 638 MB，ZGC 倾向于不立即回收（用空间换延迟）
- Rust 在 500 并发下内存仅增长 14 MB（19→33 MB）

### 1.4 冷启动时间

预编译二进制/JAR，从进程启动到首个 HTTP 响应，3 次取中位数

| | Java | Go | Rust | Kotlin |
|---|---|---|---|---|
| 启动时间 | 2,714 ms | **69 ms** | 153 ms | 914 ms |
| 相对倍数 | 39x | **1x** | 2.2x | 13x |

- Kotlin 比 Java 快 3 倍（914 vs 2714 ms），得益于 Ktor 无反射扫描/无组件注入
- Go 仍然最快，Rust 第二（含 sqlx 迁移检查）

## 2. 工程效率

### 2.1 编译时间

完全冷编译（清所有缓存，依赖已下载）

| | Java | Go | Rust | Kotlin |
|---|---|---|---|---|
| 纯编译 | **5.6s** | 9.4s | 98.3s | 14.1s |

- Kotlin 14.1s 包含 Gradle 启动开销 + K2 编译 + Shadow JAR 打包
- 增量编译（Gradle daemon 热）时 Kotlin 可降至 3-4s，接近 Java 体验

### 2.2 代码量

tokei 统计，仅计算有效代码行（不含空行和注释）

| | Java | Go | Rust | Kotlin |
|---|---|---|---|---|
| 总代码 | 7,001 | 7,087 | **4,179** | 8,607 |
| 纯手写 | 7,001 | 4,929 | **4,179** | 8,607 |
| 文件数 | 156 | 86 | **37** | 52 |

- Kotlin 代码最多（8,607 行），因为包含完整测试套件（~2,000 行测试代码）+ MockK 样板
- Go 的 sqlc 生成代码占 23%，去掉后纯手写 4,929 行

### 2.3 产物体积

| | Java | Go | Rust | Kotlin |
|---|---|---|---|---|
| 应用二进制 | ~50 MB (fat jar) | 44 MB | **19 MB** | 44 MB (fat jar) |
| Docker 镜像 | 412 MB | 150 MB | **130 MB** | 253 MB |

- Kotlin 和 Go 的二进制体积相当（44 MB）
- Kotlin Docker 镜像含 JRE (temurin-jre-alpine ~120 MB)，比 Java (zulu-jre ~260 MB) 小

## 3. 正确性

同一请求序列，对比 HTTP 状态码

| 测试用例 | Java | Go | Rust | Kotlin |
|----------|------|-----|------|--------|
| health | 200 | 200 | 200 | 200 |
| register | 200 | 201 | 201 | 201 |
| login | 200 | 200 | 200 | 200 |
| login (wrong pw) | — | 401 | 401 | 401 |
| auth/me | — | 200 | 200 | 200 |
| auth/me (no token) | — | 401 | 401 | 401 |
| video list | — | 200 | 200 | 200 |
| duplicate register | — | 409 | 409 | 409 |
| refresh token | — | 200 | 200 | 200 |
| sign-in | — | 200 | 200 | 200 |
| duplicate sign-in | — | 409 | 409 | 409 |
| follow | — | 200 | 200 | 200 |
| follow self | — | 400 | 400 | 400 |
| already following | — | 409 | 409 | 409 |
| logout + blacklist | — | 200/401 | 200/401 | 200/401 |

Go、Rust、Kotlin 行为完全一致。Java API 路径不同（`/service/api/`），未做完整覆盖。

## 4. 综合评分

| 维度 | Java | Go | Rust | Kotlin | 说明 |
|------|------|-----|------|--------|------|
| **吞吐性能** | C | B | **A** | B | Rust 2x Go ≈ Kotlin |
| **尾延迟 (p99)** | — | B | **A** | C | Kotlin 15ms vs Rust 1.5ms |
| **内存效率** | D | B | **A** | D | Kotlin 254MB ≈ Java |
| **冷启动** | D | **A** | B | C | Kotlin 比 Java 快 3x |
| **编译速度** | **A** | B | D | B | Kotlin 14s，增量 3-4s |
| **代码简洁** | B | B | **A** | B | Rust 最少，Kotlin 含测试最多 |
| **产物体积** | D | B | **A** | C | Kotlin 镜像 253MB |
| **开发生态** | **A** | A | B | A | Kotlin 共享 JVM 生态 |
| **类型安全** | B | C | **A** | B | Kotlin 空安全 + sealed class |

## 5. 场景推荐

### 选 Java 当：
- 团队已有 Java/Spring 经验
- 需要企业级生态（Spring Security, Spring Cloud）
- 内存和启动时间不敏感（传统部署，非 K8s 弹性伸缩）

### 选 Go 当：
- 最快交付速度（编译 10s + 启动 69ms + 1 周上手）
- K8s / Serverless 场景（冷启动敏感）
- 团队需要快速扩张（Go 上手最快）

### 选 Rust 当：
- 性能和内存是核心竞争力（基础设施成本敏感）
- 长期维护的核心服务（编译时安全 → 更少线上 bug）
- 团队愿意投入 3-6 个月学习曲线

### 选 Kotlin 当：
- 团队有 Java/Android 经验，想用更现代的 JVM 语言
- 需要 JVM 生态但不想用 Spring（Ktor 轻量、协程原生）
- 吞吐要求接近 Go 水平，接受 JVM 内存开销
- 重视开发体验（空安全、协程、DSL、增量编译 3-4s）

## 6. Kotlin vs Java（同 JVM 生态对比）

| 维度 | Java (Spring Boot) | Kotlin (Ktor) | 改善 |
|------|-------------------|---------------|------|
| QPS | 88K | 99K | **+12%** |
| 冷启动 | 2,714 ms | 914 ms | **3x 更快** |
| 空闲内存 | 354 MB | 254 MB | **-28%** |
| 编译 (冷) | 5.6s | 14.1s | Java 更快 |
| 编译 (增量) | ~5s | 3-4s | 接近 |
| JVM 版本 | 17 | 21 (ZGC + VT) | Kotlin 用了更新的 JVM |
| 框架开销 | 重（反射扫描、AOP 代理） | 轻（无反射、手动 DI） | Ktor 启动快的核心原因 |

结论：同一 JVM 生态下，Ktor + Kotlin + JDK 21 在吞吐和启动上显著优于 Spring Boot + Java + JDK 17。内存差距主要来自 ZGC 的堆预分配策略（可通过 -Xmx 限制）。

## 7. 测试方法论

### 工具
- **wrk** — 单端点吞吐 + 延迟分布压测（4 线程, 200/500 并发, 10 秒）
- **hyperfine** — 编译时间基准测试（3 次取均值）
- **tokei** — 代码行数统计
- **ps RSS** — 内存采样（压测期间采集）
- **python3 time.time()** — 冷启动毫秒级计时

### 公平性保障
- Go/Rust/Kotlin 共享同一 PostgreSQL 18 实例
- 四者共享同一 Redis 8 实例
- 连接池均为 lazy 模式（首次查询时建立连接）
- 编译时间均为完全冷编译（清所有缓存 + 依赖已下载）
- QPS/延迟测试均使用 wrk 统一工具、相同参数
- health 端点均在限流中间件外（公平条件）

### 局限性
- 单机 Docker 环境，非生产级基础设施
- Java 用 MySQL，其余三者用 PostgreSQL（数据库不同）
- Java API 路径不同，未做完整端到端等价测试
- Java 未重新测延迟分布数据（无 wrk 数据）
- 未测试 GraalVM native-image（可大幅改善 Java/Kotlin 启动和内存）
- Kotlin 压测时 JIT 未充分预热，峰值 QPS 可能更高
