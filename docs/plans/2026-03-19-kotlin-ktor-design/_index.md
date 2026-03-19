# Kotlin/Ktor Implementation Design

> gabon-lab 第四语言实现：基于现代 JVM 生态（Kotlin + Ktor + Virtual Threads），全面对标 Go/Rust/Java。

## Context

gabon-lab 已有 Java (Spring Boot)、Go (Echo)、Rust (Axum) 三种实现。用户希望新增 Kotlin 版本，验证现代 JVM 生态能做到什么地步。对比维度覆盖：全面对标 Go/Rust + 对比 Java 版 + 性能极限探索。

## Requirements

### Functional

- 实现与 Go/Rust 相同的全套业务 API（认证、视频管理、社交、任务系统、管理后台、报表）
- 共享 PostgreSQL 18 + Redis 8 + Garage S3 基础设施
- 复用 Go/Rust 的 PG schema（不另建迁移）
- 统一响应格式 `{ code: 0, message: "ok", data: {...} }`（与 Go/Rust 对齐）

### Non-Functional

- 端口 8090，路由前缀 `/api/v1/`, `/admin/v1/`（与 Go 一致）
- 性能 Profile 1（默认）：Coroutines + Virtual Threads + Netty + ZGC
- 性能 Profile 2（可选）：GraalVM native-image + CIO 引擎
- 可运行全套 bench 脚本（oha、k6、metrics、correctness）

## Tech Stack

| 组件 | 选择 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.3.20 |
| 框架 | Ktor (Netty) | 3.4.0 |
| ORM | Exposed DSL | 1.1.1 |
| 连接池 | HikariCP | 6.3.0 |
| 数据库 | PostgreSQL | 18 |
| 序列化 | kotlinx-serialization | 1.10.0 |
| Redis | Lettuce (coroutine ext) | 7.3.0 |
| 认证 | ktor-server-auth-jwt | 3.4.0 |
| S3 | AWS SDK for Kotlin | 1.5.5 |
| 密码 | bcrypt (favre) | 0.10.2 |
| DI | Ktor 内置 DI | 3.4.0 |
| 构建 | Gradle Kotlin DSL + K2 | 8.x |
| JVM | Eclipse Temurin | 21+ |
| GC | Generational ZGC | - |

## Rationale

- **Ktor 而非 Spring Boot**：顺 Kotlin 纹理（coroutine + DSL），退出成本低，启动快
- **Exposed DSL 而非 DAO**：性能更好、支持 R2DBC、与 sqlc/SQLx 理念对齐
- **Virtual Threads 替代 Dispatchers.IO**：JDK 21+ VT 在阻塞 I/O 场景下比 Dispatchers.IO 快 30 倍
- **Ktor 内置 DI 而非 Koin**：项目体量不大（~20 依赖），零额外依赖够用
- **Lettuce 而非 Kreds**：生态最成熟，coroutine 扩展可用

## Design Decisions

### DD-1: 跳过 Dispatchers.IO profile

用户决定不做 Dispatchers.IO 基线（Profile 1），直接从 Virtual Threads 起步。理由：已有 Java/Spring Boot 作为传统 JVM 基线，Kotlin 版本的价值在于展示现代 JVM 能做到什么。

### DD-2: 端口 8090 + Go 风格路由

选择 8090 避免与所有现有服务冲突。路由前缀 `/api/v1/` 和 `/admin/v1/` 与 Go 完全一致，方便复用 k6 脚本。

### DD-3: Exposed DSL 性能风险缓解

Exposed DSL 查询已知比原生 SQL 慢约 10x（Issue #1312）。策略：
- 常规 CRUD 用 DSL（开发效率优先）
- 热路径（CTE 点赞、原子计数器、批量 upsert）用 `exec()` 原生 SQL
- 开启 SQL logging 监控慢查询

### DD-4: 包名 lab.gabon

仓库名 gabon-lab，反转域名 `lab.gabon`，符合实验室性质。

## Reflection Summary

设计审查发现以下问题，已在实现时需注意：

### 跨文档一致性（已标准化）

| 项 | 标准值 | 说明 |
|----|--------|------|
| 端口 | 8090 | best-practices 中的 8081 示例仅为代码片段，非最终值 |
| 路由 | `/api/v1/`, `/admin/v1/` | best-practices 中简化的 `/api/` 仅为示例 |
| 包名 | `lab.gabon` | best-practices 中 `com.gabon` 仅为示例 |
| 响应格式 | `JsonData<T>`, code=0 (Int) | 与 Go/Rust 对齐 |
| bcrypt cost | 10 | 压测项目，cost=12 会拖慢认证 QPS |
| JWT scheme | `"customer"`, `"admin"` | 不加 `-jwt` 后缀 |
| 报表路径 | `/reports/video/daily` | 不是 `/reports/videos/daily`（与 Go 一致） |

### BDD 覆盖缺口（实现时需补充）

- 缺失 6 个 admin 端点场景（refresh, logout, me, admin list/get/update）
- 缺少 admin ChangePassword 的 superadmin happy path
- 缺少 "删除别人视频" 的预期行为场景
- 缺少分页参数边界验证场景
- 缺少未认证 valid-play 不触发任务进度的边界场景

## Design Documents

- [Architecture](./architecture.md) - 目录结构、依赖清单、配置、数据库 schema、错误处理、认证、中间件、S3、Virtual Threads、Docker
- [BDD Specifications](./bdd-specs.md) - 11 个 Feature、~85 个 Gherkin 场景
- [Best Practices](./best-practices.md) - 性能调优、Kotlin 惯用法、安全、测试、可观测性、构建部署、代码质量
