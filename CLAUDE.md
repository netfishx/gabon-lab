# CLAUDE.md — gabon-lab

## 项目概述

短视频平台后端四语言对比实验室。Java (Spring Boot)、Go (Echo)、Rust (Axum)、Kotlin (Ktor) 四个独立实现同一业务 API，共享基础设施，用于语言/框架/性能对比评测。

四个实现全部在本仓库，各自独立目录。

## 仓库结构

```
gabon-lab/
├── java/                # Java 实现 (Spring Boot 4.0 + Spring Data JDBC + PostgreSQL)
├── go/                  # Go 实现 (Echo + pgx + sqlc)
│   └── db/migrations/   #   goose 格式 PG migration
├── rust/                # Rust 实现 (Axum + SQLx + Tokio)
│   └── migrations/      #   sqlx 格式 PG migration
├── kotlin/              # Kotlin 实现 (Ktor + Exposed + Flyway)
├── bench/               # 对比评测脚本
├── scripts/
│   └── init-garage.sh   # Garage S3 初始化（bucket + API key）
├── docs/
│   └── benchmark-report.md  # 多语言对比评测报告
├── docker-compose.yml   # PG 18 + Redis 8 + MySQL 8.4 (legacy) + Garage S3
├── garage.toml          # Garage S3 配置
├── .env.example         # 环境变量模板
└── Makefile             # 顶层编排命令
```

## 快速开始

```bash
cp .env.example .env
make up                  # 启动所有 Docker 容器
make migrate             # Go (goose) + Rust (sqlx) + Java (Flyway) 各自迁移
make init-storage        # 初始化 Garage S3 bucket
make dev-go              # Go 服务 :8080
make dev-rust            # Rust 服务 :3000
make dev-java            # Java 服务 :8082
make dev-kotlin          # Kotlin 服务 :8090
```

## 常用命令

```bash
# 基础设施
make up / down / clean   # Docker 容器管理（clean 删除 volume）
make migrate-go          # Go: goose 迁移
make migrate-rust        # Rust: sqlx 迁移（启动时也会自动跑）
make migrate-java        # Java: Flyway 迁移
make migrate             # 全部迁移（Go + Rust + Java；Kotlin 启动时自动 Flyway）
make init-storage        # Garage S3 初始化

# 开发
make dev-go / dev-rust / dev-java / dev-kotlin   # 开发服务器
make test-go / test-rust / test-java / test-kotlin # 测试
make lint-go / lint-rust / lint-java / lint-kotlin # Lint

# 评测
make bench-oha           # 单端点 QPS 压测
make bench-k6-go         # k6 场景压测 Go
make bench-k6-rust       # k6 场景压测 Rust
make bench-k6-java       # k6 场景压测 Java
make bench-k6-kotlin     # k6 场景压测 Kotlin
make bench-metrics       # 工程指标（LOC、编译、二进制）
make bench-correctness   # 正确性验证
make bench-all           # 全部评测
```

## 技术栈对比

| 维度 | Java | Go | Rust | Kotlin |
|------|------|-----|------|--------|
| 框架 | Spring Boot 4.0 | Echo v4 + Huma v2 | Axum 0.8 + Tower | Ktor 3.4 + Netty |
| 数据库 | Spring Data JDBC + PG | pgx/v5 + sqlc | SQLx + PostgreSQL | Exposed + HikariCP |
| 缓存 | Spring Data Redis | go-redis/v9 | deadpool-redis | Lettuce 7.5 |
| 存储 | AWS SDK v2 (S3) | AWS SDK v2 (S3) | aws-sdk-s3 | AWS SDK Kotlin (S3) |
| 认证 | java-jwt | golang-jwt/v5 | jsonwebtoken | java-jwt |
| 测试 | JUnit 5 + Testcontainers | testify + mock | #[test] + trait mock | JUnit 5 |

## 共享基础设施 (Docker)

| 服务 | 镜像 | 端口 | 用途 |
|------|------|------|------|
| PostgreSQL 18 | postgres:18-alpine | 5432 | 四端共用数据库 |
| MySQL 8.4 | mysql:8.4 | 3306 | legacy（旧 Java 基线残留，可移除） |
| Redis 8 | redis:8-alpine | 6379 | 四端共用（密码：benchpass） |
| Garage S3 | dxflrs/garage:v2.2.0 | 3900 | 四端共用对象存储 |

## 服务端口

| 服务 | 端口 | API 前缀 |
|------|------|---------|
| Go | 8080 | `/api/v1/`, `/admin/v1/` |
| Rust | 3000 | `/api/`, `/admin/` |
| Java | 8082 | `/api/v1/`, `/admin/v1/` |
| Kotlin | 8090 | `/api/v1/`, `/admin/v1/` |

## 评测结论速查

| 指标 | Java | Go | Rust | Kotlin |
|------|------|-----|------|--------|
| QPS | *(pending re-benchmark)* | 96K | **182K** | *(pending)* |
| 内存 | *(pending re-benchmark)* | 43 MB | **19 MB** | *(pending)* |
| 冷启动 | *(pending re-benchmark)* | **33 ms** | 135 ms | *(pending)* |
| 编译 | *(pending re-benchmark)* | 10.3s | 97.8s | *(pending)* |
| 代码量 | *(pending re-benchmark)* | 7,087 | **4,179** | *(pending)* |

Java 列数据基于旧基线（Maven + MySQL），新实现（Spring Boot 4.0 + PG）待重新评测。详见 `docs/benchmark-report.md`。

## 设计约束

- 四个实现共享 PostgreSQL，使用统一的 int code 响应信封格式
- 各端独立管理 PG migration（Go: goose，Rust: sqlx，Java: Flyway via Gradle，Kotlin: Flyway auto），schema 相同但工具不同
- `.env` 放在仓库根目录，子项目通过 `../.env` 引用
- Rust 通过 `sqlx::migrate!()` 在启动时自动执行迁移；Kotlin 通过 Flyway 启动时自动迁移；Go 和 Java 需手动 `make migrate-go` / `make migrate-java`
