# 2026 年 Rust 后端开发最佳实践（合并版）

> 综合 Claude + Codex 调研结果，2026-03-14

## 推荐基线

- **工具链**：stable Rust 1.94.0，不要默认上 nightly
- **Edition**：`edition = "2024"`，显式设 `rust-version`
- **Workspace**：多 crate 直接 Cargo workspace，设 `resolver = "3"`
- **链接器**：Linux x86_64 上 rust-lld 自 1.90 起默认，编译体验改善
- **编译体验是一等公民**：官方调查显示编译时间和资源占用仍是主要痛点

## 框架选型

| 框架 | 定位 | 最新版本 (2026.03) | 特点 |
|------|------|-------------------|------|
| **Axum** | 模块化 API（首选） | 0.8.8 | Tower 生态、Tokio 官方、社区共识最强 |
| Actix Web | 极致性能 | 4.12.1 | 比 Axum 快 10-15%、最成熟 |
| Loco | Rails 式全栈 | 活跃开发 | 约定大于配置、单人全栈 |
| Rocket | 易上手 | 0.5.1 | 更新放缓 |

**2026 共识**：新项目首选 Axum。内部 RPC 用 tonic (gRPC/HTTP2)。

## 数据库层

| 库 | 模式 | Async | 适用 |
|----|------|-------|------|
| **SQLx** | 原生 SQL + 编译时校验 | 原生 | 首选，搭配 PG |
| SeaORM | ActiveRecord（基于 SQLx） | 原生 | CRUD 密集、Loco 内置 |
| Diesel | 类型安全查询构建器 | 插件 | 同步优先 |

- PostgreSQL 优先（SQLx 的 `query!` 宏编译时校验最完善）
- ORM 只在 CRUD 很重时再考虑 SeaORM
- 性能瓶颈几乎总在数据库本身，三者差异可忽略

## 项目结构

```
my-backend/
├── Cargo.toml        # 虚拟 manifest（不含 src/）
├── crates/
│   ├── api/          # HTTP 路由 + handler
│   ├── domain/       # 业务逻辑、实体
│   ├── infra/        # 数据库、外部 API 适配
│   └── shared/       # 错误定义、响应格式
├── migrations/
└── tests/
```

关键实践：
- 根目录用虚拟 manifest，不放 `src/`
- 起步 2-3 个 crate，感到耦合痛苦时再拆
- CI 必跑 `cargo test --workspace` + `cargo clippy --workspace` + `cargo-deny`
- 共享依赖去重可减少 70% 重复声明

## 代码与架构实践

### Handler 保持薄
参数解析 → 鉴权 → 调用 service → 映射响应。业务逻辑进 service/domain 层，数据库进 repository 层。

### 依赖注入
Axum `State<AppState>` 注入依赖，AppState 放连接池、配置、客户端、限流器等共享对象。

### Extractor 规则
只允许一个消费 body 的 extractor，必须放最后。显式配置请求体大小限制。

### 后台任务管理
所有后台任务都要有"所有者"：谁 spawn，谁负责取消、等待、清理。不散落匿名任务。

### 优雅停机
CancellationToken + TaskTracker 是当前最稳的组合。

### 错误分层
```
domain/library 层  → thiserror 定义类型化错误（编译时可穷举）
api/handler 层     → anyhow 聚合上下文（.context("创建用户失败")）
跨 async 边界      → 确保 Send + Sync
```
**不要全项目到处 anyhow。**

### 横切逻辑
超时、并发上限、body limit、trace、压缩 → Tower middleware，不写在 handler 里。

## 异步运行时

- **Tokio** 是唯一有生产共识的 async runtime
- CPU 密集用 `tokio::task::spawn_blocking`
- Hyper 0.18 支持 HTTP/3 + QUIC，单线程 1 万+ 并发

## 可观测性

```
tracing crate（结构化日志 + span）
    ↓
tracing-subscriber + OpenTelemetry Layer
    ↓
OTLP Exporter → OTel Collector → Grafana/Jaeger
```

- 默认 JSON 日志，`EnvFilter` 控制级别
- Axum 集成：`tower-http::trace::TraceLayer`
- 异步调试：`tokio-console`
- 格式化：显式设 `style_edition = "2024"`

## TLS

优先 rustls，避免 OpenSSL 系统依赖。

## 供应链安全

两层防线（2026 年已非可选）：
- `cargo-audit`：查 RustSec 漏洞
- `cargo-vet`：管"哪些第三方依赖被谁审过"
- 依赖尽量用 crates.io 正式版本，少用 `git = ...`

## 工程实践

- `cargo fmt --all -- --check` + `cargo clippy -- -Dwarnings` 固化到 CI
- 文档测试别忽略，edition 2024 对 doctest 编译性能更友好
- unsafe 不是"坏"，不清楚的 unsafe 才是，小 unsafe 岛 + 测试包围

## 部署

- 产物：单个静态二进制，~20MB，运行内存 50-80MB
- Docker：多阶段构建（`rust:slim` 编译 → `debian:bookworm-slim` 运行），镜像 <50MB

## Rust vs Go（后端场景）

| 维度 | Rust | Go |
|------|------|----|
| 性能 | 快 30%+，内存低 2-4x | IO 密集差异小 |
| 编译 | 3 分钟+ | <2 秒 |
| 上手 | 3-6 个月 | 1 周 |
| 招人 | 困难 | 容易 |
| 退出成本 | 中（学习曲线） | 低 |
| 适合 | 性能敏感、安全关键 | 快速交付、团队协作 |

**混合方案**（2026 趋势）：Go 写业务逻辑 + Rust 写性能引擎，gRPC 通信。

## 什么时候不该用 Rust

- 纯内部 CRUD、需求快速变化、团队 Rust 经验弱 → ROI 不高
- 核心复杂度在业务规则而非并发/性能/资源控制 → 收益下降
- 最在意开发吞吐而非尾延迟/内存/长期稳定性 → Go/Java 更划算
