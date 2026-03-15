# gabon-go

Go + Echo 短视频平台后端，重写原 Java Spring Boot 服务，全新 RESTful API 设计。

## 技术栈

- **Go 1.26** + **Echo v4**（HTTP 框架）
- **pgx/v5**（PostgreSQL 驱动）+ **sqlc**（类型安全 SQL 代码生成）
- **goose**（数据库迁移）
- **go-redis/v9**（token 黑名单、refresh token family）
- **golang-jwt/v5**（双域认证：客户端 + 管理员）
- **Supabase** PostgreSQL（直连）+ Storage（头像/视频上传）
- **golangci-lint** + **testify**

## 快速开始

### 前置条件

- Go 1.26+
- PostgreSQL（或 Supabase 项目）
- Redis

### 启动

```bash
cp .env.example .env
# 编辑 .env，填入数据库、Redis、Supabase 凭据

make migrate        # 运行数据库迁移
make dev            # 启动开发服务器（默认 :8080）
```

## 环境变量

完整模板见 `.env.example`。

| 变量 | 说明 |
|---|---|
| `PORT` | 服务端口（默认 `8080`） |
| `DATABASE_URL` | PostgreSQL 连接串（直连，非 pooler） |
| `REDIS_URL` | Redis 连接串 |
| `SUPABASE_URL` | Supabase 项目 URL（用于 Storage） |
| `SUPABASE_SERVICE_KEY` | Supabase service role 密钥 |
| `JWT_CUSTOMER_SECRET` | 客户端 token 签名密钥（≥32 字符） |
| `JWT_CUSTOMER_ACCESS_TTL` | 客户端 access token 有效期（如 `15m`） |
| `JWT_CUSTOMER_REFRESH_TTL` | 客户端 refresh token 有效期（如 `168h`） |
| `JWT_ADMIN_SECRET` | 管理员 token 签名密钥（≥32 字符） |
| `JWT_ADMIN_ACCESS_TTL` | 管理员 access token 有效期 |
| `JWT_ADMIN_REFRESH_TTL` | 管理员 refresh token 有效期 |
| `JWT_CURRENT_KID` | JWT 密钥轮转 Key ID |

## 项目结构

```
cmd/api/main.go                  入口，手动依赖注入
       │
internal/transport/              Echo handler + middleware + router
       │
internal/service/                业务逻辑（纯 Go，零框架依赖）
       │
internal/repository/             sqlc 生成代码 + 事务封装
       │
Supabase PostgreSQL + Redis

internal/model/                  AppError、ErrorCode、DTO
internal/config/                 环境变量加载
db/migrations/                   Goose 迁移文件
db/queries/                      sqlc SQL 查询文件
/openapi.json                    Huma 自动生成 OpenAPI 3.1（运行时端点）
```

## API 概览

### 客户端认证

| 方法 | 端点 | 认证 |
|---|---|---|
| POST | `/api/v1/auth/register` | - |
| POST | `/api/v1/auth/login` | - |
| POST | `/api/v1/auth/refresh` | - |
| POST | `/api/v1/auth/logout` | 必须 |
| GET | `/api/v1/auth/me` | 必须 |
| PUT | `/api/v1/auth/password` | 必须 |

### 用户

| 方法 | 端点 | 认证 |
|---|---|---|
| GET | `/api/v1/users/me/profile` | 必须 |
| PUT | `/api/v1/users/me/profile` | 必须 |
| POST | `/api/v1/users/me/avatar` | 必须（multipart） |
| GET | `/api/v1/users/me/following` | 必须 |
| GET | `/api/v1/users/me/followers` | 必须 |
| GET | `/api/v1/users/:id` | 可选 |
| GET | `/api/v1/users/:id/following` | - |
| GET | `/api/v1/users/:id/followers` | - |
| POST | `/api/v1/users/:id/follow` | 必须 |
| DELETE | `/api/v1/users/:id/follow` | 必须 |

### 视频

| 方法 | 端点 | 认证 |
|---|---|---|
| GET | `/api/v1/videos` | - |
| GET | `/api/v1/videos/featured` | - |
| GET | `/api/v1/videos/:id` | 可选 |
| POST | `/api/v1/videos/upload` | 必须（multipart） |
| GET | `/api/v1/videos/me` | 必须 |
| DELETE | `/api/v1/videos/:id` | 必须 |
| POST | `/api/v1/videos/:id/like` | 必须 |
| DELETE | `/api/v1/videos/:id/like` | 必须 |
| POST | `/api/v1/videos/:id/play-click` | 可选 |
| POST | `/api/v1/videos/:id/play-valid` | 可选 |
| GET | `/api/v1/users/:id/videos` | 可选 |

### 任务

| 方法 | 端点 | 认证 |
|---|---|---|
| GET | `/api/v1/tasks` | 必须 |
| POST | `/api/v1/tasks/:progressId/claim` | 必须 |

### 管理员认证

| 方法 | 端点 | 认证 |
|---|---|---|
| POST | `/admin/v1/auth/login` | - |
| POST | `/admin/v1/auth/logout` | 管理员 |
| GET | `/admin/v1/auth/me` | 管理员 |

### 管理员用户管理

| 方法 | 端点 | 认证 |
|---|---|---|
| GET | `/admin/v1/admin-users` | 管理员 |
| POST | `/admin/v1/admin-users` | 超级管理员 |
| GET | `/admin/v1/admin-users/:id` | 管理员 |
| PUT | `/admin/v1/admin-users/:id` | 管理员 |
| DELETE | `/admin/v1/admin-users/:id` | 超级管理员 |
| PUT | `/admin/v1/admin-users/:id/password` | 管理员 |

### 管理员客户管理

| 方法 | 端点 | 认证 |
|---|---|---|
| GET | `/admin/v1/customers` | 管理员 |
| PUT | `/admin/v1/customers/:id/password` | 管理员 |

### 管理员视频管理

| 方法 | 端点 | 认证 |
|---|---|---|
| GET | `/admin/v1/videos` | 管理员 |
| GET | `/admin/v1/videos/:id` | 管理员 |
| POST | `/admin/v1/videos/:id/review` | 管理员 |
| DELETE | `/admin/v1/videos/:id` | 管理员 |

### 管理员报表

| 方法 | 端点 | 认证 |
|---|---|---|
| GET | `/admin/v1/reports/revenue` | 管理员 |
| GET | `/admin/v1/reports/video/daily` | 管理员 |
| GET | `/admin/v1/reports/video/summary` | 管理员 |

### 健康检查

| 方法 | 端点 | 认证 |
|---|---|---|
| GET | `/health` | - |

## 常用命令

```bash
make dev              # 启动开发服务器
make build            # 编译到 bin/api
make lint             # 运行 golangci-lint
make test             # 运行单元测试（带 race 检测）
make test-integration # 运行集成测试
make migrate          # 执行待执行的迁移（需 DATABASE_URL）
make migrate-down     # 回滚上一次迁移
make migrate-status   # 查看迁移状态
make sqlc             # 重新生成 sqlc 代码
```

## 响应格式

所有端点返回统一 JSON 信封：

```json
{
  "code": "OK",
  "message": "ok",
  "data": { ... }
}
```

错误响应使用相同结构，`code` 为 ErrorCode 枚举值：

```json
{
  "code": "AUTH_INVALID_CREDENTIALS",
  "message": "username or password is incorrect",
  "data": null
}
```

## 接口文档

Huma 框架从 Go 类型自动生成 OpenAPI 3.1 文档，运行时访问：

```bash
# 启动服务后
curl http://localhost:8080/openapi.json
```
