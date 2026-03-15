# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Go + Echo 后端服务，重写 gabon（Java Spring Boot），作为可替换备选后端。API 重新设计（RESTful 最佳实践），不对标原有接口。

短视频平台后端：用户认证、视频管理（上传/审核/播放）、社交互动（关注/点赞）、任务奖励系统、后台管理。

## Tech Stack

Go 1.26, Echo v4, pgx/v5, sqlc, goose, golang-jwt/v5, go-redis/v9, slog, validator/v10, testify, golangci-lint

- **数据库**: Supabase PostgreSQL，直连 5432（非 pooler），pgx 默认 prepared statement 不兼容 PgBouncer transaction mode
- **SQL 生成**: sqlc — 原生 SQL + 类型安全
- **Redis**: token 黑名单 + refresh token family

## Commands

```bash
make dev              # go run cmd/api/main.go
make build            # go build -o bin/api cmd/api/main.go
make lint             # golangci-lint run ./...
make test             # go test -v -race -count=1 ./...
make test-integration # go test -v -race -count=1 -tags=integration ./...
make migrate          # goose up (需 DATABASE_URL 环境变量)
make migrate-down     # goose down
make migrate-status   # goose status
make sqlc             # sqlc generate
```

## Architecture

三层单向依赖，不能跳层：

```
cmd/api/main.go                  启动 + 手动依赖注入（无 DI 框架）
       │
internal/transport/              Echo handler + middleware + router（唯一碰 Echo 的层）
       │
internal/service/                业务接口 + 实现（纯 Go，零框架依赖）
       │
internal/repository/             sqlc 生成代码 + 事务封装（只碰 pgx）
       │
Supabase PostgreSQL + Redis
```

### Key Directories

- `cmd/api/main.go` — 入口，手动构造依赖链
- `internal/transport/` — HTTP handler、router.go、response.go（统一响应）、middleware/
- `internal/service/` — 业务逻辑，通过 interface 解耦 repository
- `internal/repository/` — sqlc 生成，禁止手写
- `internal/model/` — AppError、ErrorCode、DTO
- `internal/config/` — 环境变量加载
- `db/migrations/` — goose 迁移文件
- `db/queries/` — sqlc SQL 查询文件

## Route Structure

单体服务，路由前缀区分角色：

- `/api/v1/...` — 客户端 API
- `/admin/v1/...` — 后台管理 API（中间件校验管理员角色）
- `/health` — 健康检查

## Critical Design Constraints

### Unified Response Format

所有 API 响应必须包含 `code` (string) + `message` + `data`。成功固定 `"OK"`，错误为 ErrorCode 枚举值：

```json
{ "code": "OK", "message": "ok", "data": { ... } }
{ "code": "AUTH_INVALID_CREDENTIALS", "message": "...", "data": null }
```

### Auth Domain Isolation

客户端和管理员 token 使用不同的 iss/aud/签名密钥，中间件必须同时验证三者：

| 域 | iss | aud | 密钥环境变量 |
|---|---|---|---|
| 客户端 | gabon-service | customer | JWT_CUSTOMER_SECRET |
| 管理员 | gabon-admin | admin | JWT_ADMIN_SECRET |

### Concurrency Safety Rules

- **计数器字段**（like_count, total_clicks 等）必须原子 SQL 更新 `SET col = col + 1`，禁止 read-modify-write
- **点赞/取消点赞**: 用 CTE 确保仅在 INSERT/DELETE 成功时才增减计数
- **任务领取**: 事务内 `SELECT ... FOR UPDATE` 行锁 + 应用层状态校验 + 原子加钻石
- **Refresh Token**: Redis Lua 脚本原子 CAS（先签发再 CAS，CAS 失败丢弃预生成 token）

### Username Handling

用户名唯一约束用 partial index `LOWER(username) WHERE deleted_at IS NULL`。注册/登录查询统一用 `LOWER(username)`。

### Period Key (Task System)

业务时区固定 `Asia/Shanghai`。service 层提供 `PeriodKey(taskType, now)` 统一生成，禁止各处自行格式化：
- 每日: `2026-02-19`
- 每周: `2026-W08` (ISO 8601, 周一起始)
- 每月: `2026-02`

### Soft Delete

admin_users、customers、videos 使用 `deleted_at` 软删除，查询条件需包含 `WHERE deleted_at IS NULL`。

## Testing

| 层 | 方式 |
|---|---|
| Repository | testify + 真实 PG |
| Service | testify + mock repo (interface) |
| Transport | httptest + mock service |
| 集成 | 完整启动 + 真实 DB |

**必须覆盖的并发测试**（CI 门禁，不可推迟）：
- 并发 refresh — 同一旧 token 并发请求仅一个成功
- logout 后 refresh 失效
- 并发点赞 — like_count 只增 1
- 并发任务领取 — 仅一次成功加钻石

## Design Documents

详细设计（数据模型、API 端点完整列表、认证流程、Lua 脚本等）见：
- `docs/plans/2026-02-19-gabon-go-design.md` — 设计文档
- `docs/plans/2026-02-19-gabon-go-impl.md` — 实现计划（分步任务）
