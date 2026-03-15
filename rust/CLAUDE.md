# CLAUDE.md — gabon-rust

## 项目概述

Orbit 短视频平台后端 Rust 实现。与 gabon-go（Go + Echo）各自独立实现同一业务，用于 Rust vs Go 后端对比测试。

共享基础设施（同一个 Supabase PG / Redis），API 设计各自独立。

## 技术栈

| 类别 | 选型 | gabon-go 对应 |
|------|------|--------------|
| HTTP | Axum 0.8 + Tower + Tokio | Echo v4 |
| 数据库 | SQLx + PostgreSQL (Supabase) | pgx/v5 + sqlc |
| 缓存 | deadpool-redis | go-redis/v9 |
| 认证 | jsonwebtoken (JWT) | golang-jwt/v5 |
| 存储 | Supabase Storage | Supabase Storage |
| 可观测 | tracing + tracing-subscriber | slog + OTel |
| 错误 | thiserror (domain) + anyhow (handler) | AppError + ErrorCode |
| TLS | rustls | — |

## 项目结构

```
crates/
├── api/       # Axum 路由 + handler（薄层）       ← transport/
├── domain/    # 业务逻辑、实体、服务层              ← service/
├── infra/     # SQLx repo、Redis、S3 适配          ← repository/
└── shared/    # 配置、错误类型、响应格式、分页        ← model/ + config/
```

## 常用命令

```bash
make dev              # cargo run -p gabon-api
make build            # cargo build --release
make check            # cargo check --workspace
make lint             # cargo clippy -- -Dwarnings
make fmt              # cargo fmt --all -- --check
make test             # cargo test --workspace
make migrate          # sqlx migrate run
make migrate-down     # sqlx migrate revert
make docker-build     # docker build
make docker-run       # docker run with .env
```

## 架构原则

- Handler 保持薄：参数解析 → 调用 service → 映射响应
- 错误分层：domain 用 thiserror 类型化错误，api 层用 anyhow 兜底
- 依赖注入通过 Axum State<AppState>，AppState 放连接池/配置/客户端
- 横切逻辑（超时、限流、trace、压缩）放 Tower middleware
- SQL 手写，利用 SQLx 编译时校验
- JWT 双密钥隔离：customer 和 admin 使用不同的 secret/TTL

## 配置

环境变量通过 `.env` 加载，参考 `.env.example`。与 gabon-go 共享同一套基础设施连接。

## 参考

- gabon-go 源码：`/Users/ethanwang/projects/gabon-go/`
- gabon (Java) 源码：`/Users/ethanwang/projects/gabon/`
- 调研文档：`docs/rust-backend-guide.md`
