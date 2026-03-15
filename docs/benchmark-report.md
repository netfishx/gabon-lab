# Gabon 后端三语言对比评测报告

> 测试日期：2026-03-15
> 测试环境：macOS Darwin 25.4.0, Apple Silicon, Docker (OrbStack)
> 数据库：PostgreSQL 18 (Docker) / MySQL 8.4 (Docker, Java only)
> 缓存：Redis 8 (Docker)
> 存储：Garage S3 v2.2.0 (Docker)

## 项目概述

三个独立实现同一短视频平台后端 API，用于语言/框架对比：

| | Java | Go | Rust |
|---|---|---|---|
| 框架 | Spring Boot 3.2.4 | Echo v4 + Huma v2 | Axum 0.8 |
| 数据库 | MyBatis-Plus + MySQL | pgx/v5 + sqlc | SQLx + PostgreSQL |
| 运行时 | JVM (Zulu 17) | Go 1.26 | Tokio (Rust 1.94) |
| 缓存 | Jedis + Redisson | go-redis/v9 | deadpool-redis |

## 1. 性能

### 1.1 吞吐量 (QPS)

测试工具：oha，持续 10 秒，200 并发

| 端点 | Java | Go | Rust |
|------|------|-----|------|
| health (无 DB) | 88,149 | 96,260 | **181,607** |
| 相对倍数 | 1x | 1.09x | **2.06x** |

### 1.2 运行时内存 (RSS)

500 并发压测，采样 4 次取稳定值

| 阶段 | Java | Go | Rust |
|------|------|-----|------|
| 空闲 | 354 MB | 43 MB | **19 MB** |
| 压测中 (峰值) | 372 MB | 52 MB | **19 MB** |
| 压测后 | 370 MB | 54 MB | **19 MB** |

**Rust 在 500 并发下内存零增长**，Go 增长 21%，Java 增长 5%（GC 持续回收）

### 1.3 冷启动时间

预编译二进制，从进程启动到首个 HTTP 响应，3 次取中位数

| | Java | Go | Rust |
|---|---|---|---|
| 启动时间 | 2,714 ms | **33 ms** | 135 ms |
| 相对倍数 | 82x | **1x** | 4x |

## 2. 工程效率

### 2.1 编译时间

清编译缓存，依赖缓存保留（公平条件）

| | Java | Go | Rust |
|---|---|---|---|
| 纯编译 | **5.6s** | 10.3s | 97.8s |
| 含依赖下载 (首次) | ~27s | ~30s (估) | ~5min (估) |

Java 的 `javac` 只生成字节码（不做原生代码生成和链接），是三者中最快的编译器

### 2.2 代码量

tokei 统计，仅计算有效代码行（不含空行和注释）

| | Java | Go | Rust |
|---|---|---|---|
| 手写代码 | 7,001 | 7,087 (含 sqlc 生成 2,158) | **4,179** |
| 纯手写 | 7,001 | 4,929 | **4,179** |
| 文件数 | 156 | 86 | **37** |

Go 的 sqlc 生成代码占总量 23%。去掉后三者手写量：Java ≈ Go > Rust

### 2.3 产物体积

| | Java | Go | Rust |
|---|---|---|---|
| 应用二进制 | ~50 MB (fat jar) | 44 MB | **19 MB** |
| Docker 镜像 (debian-slim) | 412 MB | 150 MB | **130 MB** |

Java 镜像含 JRE (~260 MB)，Go/Rust 基础镜像相同 (debian:bookworm-slim ~80 MB)

## 3. 正确性

同一请求序列，对比 HTTP 状态码

| 测试用例 | Java | Go | Rust |
|----------|------|-----|------|
| health | 200 | 200 | 200 |
| register | 200 | 200 | 200 |
| login | 200 | 200 | 200 |
| login (wrong pw) | — | 401 | 401 |
| auth/me | — | 200 | 200 |
| auth/me (no token) | — | 401 | 401 |
| video list | — | 200 | 200 |
| duplicate register | — | 409 | 409 |

Go 和 Rust 行为完全一致。Java API 路径不同（`/service/api/`），未做完整覆盖

## 4. 综合评分

| 维度 | Java | Go | Rust | 说明 |
|------|------|-----|------|------|
| **吞吐性能** | C | B | **A** | Rust 2x Go, 2x Java |
| **内存效率** | D | B | **A** | Rust 19MB vs Java 354MB (18x) |
| **冷启动** | D | **A** | B | Go 33ms, Java 2.7s (82x) |
| **编译速度** | **A** | B | D | Java 5.6s, Rust 98s (17x) |
| **代码简洁** | B | B | **A** | Rust 最少代码 + 最少文件 |
| **产物体积** | D | B | **A** | Rust 二进制 19MB, 镜像 130MB |
| **开发生态** | **A** | A | B | Java/Go 库更成熟 |
| **类型安全** | B | C | **A** | Rust 编译时最强保障 |

## 5. 场景推荐

### 选 Java 当：
- 团队已有 Java/Spring 经验
- 需要企业级生态（Spring Security, Spring Cloud）
- 内存和启动时间不敏感（传统部署，非 K8s 弹性伸缩）

### 选 Go 当：
- 最快交付速度（编译 10s + 启动 33ms + 1 周上手）
- K8s / Serverless 场景（冷启动敏感）
- 团队需要快速扩张（Go 上手最快）

### 选 Rust 当：
- 性能和内存是核心竞争力（基础设施成本敏感）
- 长期维护的核心服务（编译时安全 → 更少线上 bug）
- 团队愿意投入 3-6 个月学习曲线

## 6. 测试方法论

### 工具
- **oha** 1.14.0 — 单端点吞吐压测
- **k6** — 场景化负载测试
- **tokei** — 代码行数统计
- **ps RSS** — 内存采样（压测期间每 5s 采集）
- **python3 time.time()** — 毫秒级计时

### 公平性保障
- Go/Rust 共享同一 PostgreSQL 18 实例
- 三者共享同一 Redis 8 实例
- 连接池均为 lazy 模式（首次查询时建立连接）
- Docker 镜像基础统一为 debian:bookworm-slim（Java 用 zulu-jre-debian）
- 编译时间均为清缓存 + 依赖已下载

### 局限性
- 单机 Docker 环境，非生产级基础设施
- Java 用 MySQL，Go/Rust 用 PostgreSQL（数据库不同）
- Java API 路径不同，未做完整端到端等价测试
- 未测试高并发下的错误率和尾延迟 (p99)
- 未测试 GraalVM native-image（可大幅改善 Java 启动和内存）
