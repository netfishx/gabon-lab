# CLAUDE.md — gabon-lab

## 项目概述

短视频平台后端三语言对比实验室。Java (Spring Boot)、Go (Echo)、Rust (Axum) 三个独立实现同一业务 API，共享基础设施，用于语言/框架/性能对比评测。

三个实现全部在本仓库，各自独立目录。

## 仓库结构

```
gabon-lab/
├── java/                # Java 实现 (Spring Boot + MyBatis-Plus + MySQL)
├── go/                  # Go 实现 (Echo + pgx + sqlc)
│   └── db/migrations/   #   goose 格式 PG migration
├── rust/                # Rust 实现 (Axum + SQLx + Tokio)
│   └── migrations/      #   sqlx 格式 PG migration
├── bench/               # 对比评测脚本
├── scripts/
│   └── init-garage.sh   # Garage S3 初始化（bucket + API key）
├── docs/
│   └── benchmark-report.md  # 三语言对比评测报告
├── docker-compose.yml   # PG 18 + Redis 8 + MySQL 8.4 + Garage S3
├── garage.toml          # Garage S3 配置
├── .env.example         # 环境变量模板
└── Makefile             # 顶层编排命令
```

## 快速开始

```bash
cp .env.example .env
make up                  # 启动所有 Docker 容器
make migrate             # Go (goose) + Rust (sqlx) 各自迁移
make init-storage        # 初始化 Garage S3 bucket
make dev-go              # Go 服务 :8080
make dev-rust            # Rust 服务 :3000
```

## 常用命令

```bash
# 基础设施
make up / down / clean   # Docker 容器管理（clean 删除 volume）
make migrate-go          # Go: goose 迁移
make migrate-rust        # Rust: sqlx 迁移（启动时也会自动跑）
make migrate             # 两端同时迁移
make init-storage        # Garage S3 初始化

# 开发
make dev-go / dev-rust   # 开发服务器
make test-go / test-rust # 测试
make lint-go / lint-rust # Lint

# 评测
make bench-oha           # 单端点 QPS 压测
make bench-k6-go         # k6 场景压测 Go
make bench-k6-rust       # k6 场景压测 Rust
make bench-metrics       # 工程指标（LOC、编译、二进制）
make bench-correctness   # 正确性验证
make bench-all           # 全部评测
```

## 技术栈对比

| 维度 | Java (../gabon/) | Go | Rust |
|------|-----------------|-----|------|
| 框架 | Spring Boot 3.2 | Echo v4 + Huma v2 | Axum 0.8 + Tower |
| 数据库 | MyBatis-Plus + MySQL | pgx/v5 + sqlc | SQLx + PostgreSQL |
| 缓存 | Jedis + Redisson | go-redis/v9 | deadpool-redis |
| 存储 | AWS S3 | AWS SDK v2 (S3) | aws-sdk-s3 |
| 认证 | JWT (jjwt) | golang-jwt/v5 | jsonwebtoken |
| 测试 | JUnit | testify + mock | #[test] + trait mock |

## 共享基础设施 (Docker)

| 服务 | 镜像 | 端口 | 用途 |
|------|------|------|------|
| PostgreSQL 18 | postgres:18-alpine | 5432 | Go + Rust 数据库 |
| MySQL 8.4 | mysql:8.4 | 3306 | Java 数据库 |
| Redis 8 | redis:8-alpine | 6379 | 三端共用（密码：benchpass） |
| Garage S3 | dxflrs/garage:v2.2.0 | 3900 | Go + Rust 对象存储 |

## 服务端口

| 服务 | 端口 | API 前缀 |
|------|------|---------|
| Go | 8080 | `/api/v1/`, `/admin/v1/` |
| Rust | 3000 | `/api/`, `/admin/` |
| Java | 8082 | `/service/api/` |

## 评测结论速查

| 指标 | Java | Go | Rust |
|------|------|-----|------|
| QPS | 88K | 96K | **182K** |
| 内存 | 354 MB | 43 MB | **19 MB** |
| 冷启动 | 2,714 ms | **33 ms** | 135 ms |
| 编译 | **5.6s** | 10.3s | 97.8s |
| 代码量 | 7,001 | 7,087 | **4,179** |

详见 `docs/benchmark-report.md`。

## 设计约束

- API 设计各自独立，不强制对齐路径和响应格式
- Go 和 Rust 各自独立管理 PG migration（Go: goose，Rust: sqlx），schema 相同但工具不同
- Java 独立用 MySQL，schema 在 `java/gabon-service/src/main/resources/sql/`
- `.env` 放在仓库根目录，子项目通过 `../.env` 引用
- Rust 通过 `sqlx::migrate!()` 在启动时自动执行迁移；Go 需手动 `make migrate-go`
