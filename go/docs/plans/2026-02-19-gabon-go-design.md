# Gabon Go — 设计文档

> 用 Go + Echo 重写 gabon（Java Spring Boot）后端，作为可替换的备选后端。
> API 重新设计（RESTful 最佳实践），不对标 gabon 原有接口。

## 1. 技术选型

| 职责 | 选择 | 说明 |
|------|------|------|
| HTTP 框架 | Echo v4 | 仅限 transport 层，业务层零 Echo 依赖 |
| 数据库 | Supabase PostgreSQL | utf8, UTC, 外键约束 |
| 数据库驱动 | pgx/v5 | Go PG 生态标准 |
| 连接模式 | 直连 5432（非 pooler） | 避免 PgBouncer transaction mode 的 prepared statement 问题 |
| SQL 生成 | sqlc | 类型安全，原生 SQL |
| 数据库迁移 | goose | 支持 PG |
| JWT | golang-jwt/jwt/v5 | 短期 access + 长期 refresh，kid 轮换 |
| 配置 | .env（本地）/ AWS SSM（生产） | 分层配置 |
| 日志 | log/slog | 标准库结构化日志 |
| 校验 | go-playground/validator/v10 | |
| Redis | go-redis/redis/v9 | token 黑名单 |
| 密码 | golang.org/x/crypto/bcrypt | |
| 测试 | testing + testify | 单测 + 集成测试 |
| Lint | golangci-lint | CI 门禁 |

## 2. 架构

### 2.1 分层

```
cmd/api/main.go                  启动 + 手动依赖注入
       │
internal/transport/              Echo handler + middleware + router
       │                         唯一碰 Echo 的地方
internal/service/                业务接口 + 实现（纯 Go）
       │
internal/repository/             sqlc 生成 + 事务封装（只碰 pgx）
       │
Supabase PostgreSQL + Redis
```

层级严格单向依赖，不能跳层。

**Supabase 连接注意**：使用直连端口 5432，不走 Supabase pooler（6543）。
pgx/v5 默认使用 prepared statement，PgBouncer transaction mode 不兼容。
若未来需要 pooler，须配置 pgx `default_query_exec_mode=simple_protocol` 并禁用 statement cache。

### 2.2 路由

单体服务，路由前缀区分角色：

- `/api/v1/...` — 客户端 API
- `/admin/v1/...` — 后台管理 API（中间件校验管理员角色）
- `/health` — 健康检查

### 2.3 依赖注入

main.go 手动构造，不用 DI 框架：

```go
db := pgxpool.New(cfg.DatabaseURL)
repo := repository.New(db)
authSvc := service.NewAuthService(repo, rdb, jwtCfg)
handler := transport.NewHandler(authSvc, videoSvc, ...)
e := echo.New()
transport.RegisterRoutes(e, handler)
e.Start(":8080")
```

## 3. 数据模型

### 3.1 admin_users

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| username | VARCHAR(100) NOT NULL | 活跃记录唯一（partial index） |
| password_hash | VARCHAR(255) NOT NULL | bcrypt |
| role | SMALLINT NOT NULL DEFAULT 2 | 1=超管, 2=普通 |
| full_name | VARCHAR(255) | |
| phone | VARCHAR(50) | |
| avatar_url | VARCHAR(500) | |
| status | SMALLINT NOT NULL DEFAULT 1 | 0=禁用, 1=启用 |
| last_login_at | TIMESTAMPTZ | |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |
| deleted_at | TIMESTAMPTZ | 软删除 |

**索引**：`CREATE UNIQUE INDEX idx_admin_users_username_active ON admin_users(LOWER(username)) WHERE deleted_at IS NULL;`
用户名唯一约束不区分大小写（LOWER），注册/登录时统一 `LOWER(username)` 查询。

### 3.2 customers

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| username | VARCHAR(100) NOT NULL | 活跃记录唯一（partial index） |
| password_hash | VARCHAR(255) NOT NULL | bcrypt |
| name | VARCHAR(255) | |
| phone | VARCHAR(50) | |
| email | VARCHAR(255) | |
| avatar_url | VARCHAR(500) | |
| signature | VARCHAR(255) | 个性签名 |
| is_vip | BOOLEAN NOT NULL DEFAULT FALSE | |
| diamond_balance | BIGINT NOT NULL DEFAULT 0 | 钻石余额 |
| withdrawal_password_hash | VARCHAR(255) | 取款密码 |
| last_login_at | TIMESTAMPTZ | |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |
| deleted_at | TIMESTAMPTZ | |

**索引**：`CREATE UNIQUE INDEX idx_customers_username_active ON customers(LOWER(username)) WHERE deleted_at IS NULL;`
用户名唯一约束不区分大小写（LOWER），注册/登录时统一 `LOWER(username)` 查询。

### 3.3 videos

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| customer_id | BIGINT NOT NULL FK→customers | |
| title | VARCHAR(500) | |
| description | TEXT | |
| file_name | VARCHAR(255) NOT NULL | |
| file_size | BIGINT NOT NULL | 字节 |
| file_url | VARCHAR(500) NOT NULL | |
| thumbnail_url | VARCHAR(500) | |
| preview_gif_url | VARCHAR(500) | |
| mime_type | VARCHAR(100) NOT NULL | |
| duration | INT | 秒 |
| width | INT | |
| height | INT | |
| status | SMALLINT NOT NULL DEFAULT 1 | 0=失败,1=待转码,2=转码中,3=待审核,4=通过,5=拒绝 |
| review_notes | TEXT | |
| reviewed_by | BIGINT FK→admin_users | |
| reviewed_at | TIMESTAMPTZ | |
| total_clicks | BIGINT NOT NULL DEFAULT 0 | |
| valid_clicks | BIGINT NOT NULL DEFAULT 0 | |
| like_count | BIGINT NOT NULL DEFAULT 0 | |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |
| deleted_at | TIMESTAMPTZ | |

**并发安全**：计数器字段（total_clicks, valid_clicks, like_count）必须用原子 SQL 更新：
`UPDATE videos SET like_count = like_count + 1 WHERE id = $1`，禁止 read-modify-write。

### 3.4 video_play_records

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| video_id | BIGINT NOT NULL FK→videos | |
| customer_id | BIGINT FK→customers | 游客为 NULL |
| play_type | SMALLINT NOT NULL | 1=点击, 2=有效播放 |
| ip_address | VARCHAR(45) | |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |

### 3.5 video_likes

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| video_id | BIGINT NOT NULL FK→videos | |
| customer_id | BIGINT NOT NULL FK→customers | |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |
| UNIQUE(video_id, customer_id) | | |

**并发安全**：点赞用 CTE 确保仅在 insert 成功时才增量：
```sql
WITH inserted AS (
    INSERT INTO video_likes (video_id, customer_id)
    VALUES ($1, $2)
    ON CONFLICT (video_id, customer_id) DO NOTHING
    RETURNING id
)
UPDATE videos SET like_count = like_count + 1
WHERE id = $1 AND EXISTS (SELECT 1 FROM inserted);
```
取消点赞同理，DELETE RETURNING + 条件 decrement。

### 3.6 user_follows

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| follower_id | BIGINT NOT NULL FK→customers | |
| followed_id | BIGINT NOT NULL FK→customers | |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |
| UNIQUE(follower_id, followed_id) | | |
| CHECK(follower_id != followed_id) | | |

### 3.7 task_definitions

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| task_code | VARCHAR(100) UNIQUE NOT NULL | 如 DAILY_WATCH_50 |
| task_name | VARCHAR(255) NOT NULL | |
| description | TEXT | |
| task_type | SMALLINT NOT NULL | 1=每日,2=每周,3=每月 |
| task_category | SMALLINT NOT NULL | 1=观看,2=上传,3=分享,4=点赞,5=登录 |
| target_count | INT NOT NULL | |
| reward_diamonds | INT NOT NULL | |
| icon_url | VARCHAR(500) | |
| display_order | INT NOT NULL DEFAULT 0 | |
| vip_only | BOOLEAN NOT NULL DEFAULT FALSE | |
| status | SMALLINT NOT NULL DEFAULT 1 | 0=禁用,1=启用 |
| start_time | TIMESTAMPTZ | |
| end_time | TIMESTAMPTZ | |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |

### 3.8 task_progress

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| customer_id | BIGINT NOT NULL FK→customers | |
| task_id | BIGINT NOT NULL FK→task_definitions | |
| current_count | INT NOT NULL DEFAULT 0 | |
| target_count | INT NOT NULL | |
| period_key | VARCHAR(50) NOT NULL | 见下方 period_key 规则 |
| task_status | SMALLINT NOT NULL DEFAULT 1 | 1=进行中,2=已完成,3=已领取,4=已过期 |
| reward_diamonds | INT NOT NULL | |
| completed_at | TIMESTAMPTZ | |
| claimed_at | TIMESTAMPTZ | |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |
| updated_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |
| UNIQUE(customer_id, task_id, period_key) | | |

**period_key 规则**：
- 业务时区固定 `Asia/Shanghai`（UTC+8），所有 period 计算基于此时区
- 每日：`2026-02-19`（上海时区当日 00:00 ~ 23:59）
- 每周：`2026-W08`（ISO 8601 周，周一为起始日）
- 每月：`2026-02`（上海时区自然月）
- service 层提供 `PeriodKey(taskType, now)` 函数统一生成，禁止各处自行格式化

**并发安全**：任务领取必须在事务内执行（`{progressId}` 即 task_progress.id）：
```sql
-- Step 1: 行锁 + 归属校验
-- $1 = progressId, $2 = customerId
SELECT id, reward_diamonds, task_status
FROM task_progress
WHERE id = $1 AND customer_id = $2
FOR UPDATE;
-- Step 2: 应用层校验 task_status = 2（已完成），否则返回 TASK_NOT_CLAIMABLE
-- Step 3: 原子加钻石
-- $1 = rewardDiamonds, $2 = customerId
UPDATE customers
SET diamond_balance = diamond_balance + $1
WHERE id = $2;
-- Step 4: 标记已领取
-- $1 = progressId
UPDATE task_progress
SET task_status = 3, claimed_at = NOW()
WHERE id = $1;
```

**注意**：API 路径 `/tasks/{progressId}/claim` 的参数是 task_progress 主键，不是 task_definition 主键。

## 4. API 设计

统一响应格式（所有响应必须包含 code + message + data 三个字段，**code 统一为 string 类型**）：

```json
{ "code": "OK", "message": "ok", "data": { ... } }
```

分页响应（data 内嵌分页结构）：

```json
{
  "code": "OK",
  "message": "ok",
  "data": {
    "items": [...],
    "total": 100,
    "page": 1,
    "page_size": 20
  }
}
```

错误响应：

```json
{ "code": "AUTH_INVALID_CREDENTIALS", "message": "用户名或密码错误", "data": null }
```

**code 类型说明**：统一为 string，成功时固定为 `"OK"`，错误时为对应 ErrorCode 枚举值。
强类型客户端只需判断 `code == "OK"` 即可区分成功/失败，无需处理 int/string 混合类型。

### 4.1 客户端 API `/api/v1`

#### 认证 `/auth`

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | /register | 注册 | 无 |
| POST | /login | 登录 | 无 |
| POST | /refresh | 刷新 token | refresh token |
| POST | /logout | 登出 | access token |
| GET | /me | 当前用户信息 | access token |
| PUT | /password | 修改密码 | access token |

#### 用户 `/users`

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | /me/profile | 我的资料 | 必须 |
| PUT | /me/profile | 更新资料 | 必须 |
| POST | /me/avatar-upload-url | 头像上传 URL | 必须 |
| GET | /me/following | 我的关注 | 必须 |
| GET | /me/followers | 我的粉丝 | 必须 |
| GET | /{id} | 他人主页 | 可选 |
| GET | /{id}/following | 他人关注列表 | 可选 |
| GET | /{id}/followers | 他人粉丝列表 | 可选 |
| POST | /{id}/follow | 关注 | 必须 |
| DELETE | /{id}/follow | 取消关注 | 必须 |

#### 视频 `/videos`

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | / | 首页视频列表 | 无 |
| GET | /featured | 热门视频 | 无 |
| GET | /{id} | 视频详情 | 可选 |
| POST | /upload-url | 获取上传 URL | 必须 |
| POST | /confirm-upload | 确认上传 | 必须 |
| GET | /me | 我的视频 | 必须 |
| DELETE | /{id} | 删除视频 | 必须 |
| POST | /{id}/like | 点赞 | 必须 |
| DELETE | /{id}/like | 取消点赞 | 必须 |
| POST | /{id}/play-click | 记录点击 | 可选 |
| POST | /{id}/play-valid | 记录有效播放 | 可选 |

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | /users/{id}/videos | 他人视频列表 | 可选 |

#### 任务 `/tasks`

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | / | 任务列表 | 必须 |
| POST | /{progressId}/claim | 领取奖励（参数为 task_progress.id） | 必须 |

### 4.2 后台 API `/admin/v1`

#### 认证 `/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /login | 管理员登录 |
| POST | /logout | 管理员登出 |
| GET | /me | 当前管理员信息 |

#### 管理员 `/admin-users`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | / | 管理员列表 |
| POST | / | 创建管理员 |
| GET | /{id} | 管理员详情 |
| PUT | /{id} | 更新管理员 |
| DELETE | /{id} | 删除管理员 |
| PUT | /{id}/password | 改密码 |

#### 客户管理 `/customers`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | / | 客户列表 |
| PUT | /{id}/password | 重置客户密码 |

#### 视频审核 `/videos`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | / | 视频列表 |
| GET | /{id} | 视频详情 |
| POST | /{id}/review | 审核视频 |
| DELETE | /{id} | 删除视频 |

#### 报表 `/reports`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /revenue | 营收报表 |
| GET | /video/daily | 每日视频报表 |
| GET | /video/summary | 视频汇总报表 |

## 5. 错误处理

### 5.1 AppError

```go
type ErrorCode string
type AppError struct {
    Code    ErrorCode
    Message string
    Status  int   // HTTP status
    Err     error // 原始 error，支持 Unwrap
}
```

### 5.2 错误码

| 错误码 | HTTP | 场景 |
|--------|------|------|
| INTERNAL_ERROR | 500 | 未预期错误 |
| BAD_REQUEST | 400 | 参数校验失败 |
| NOT_FOUND | 404 | 资源不存在 |
| UNAUTHORIZED | 401 | 未认证 |
| FORBIDDEN | 403 | 无权限 |
| AUTH_INVALID_CREDENTIALS | 401 | 用户名/密码错误 |
| AUTH_TOKEN_EXPIRED | 401 | token 过期 |
| AUTH_TOKEN_INVALID | 401 | token 无效 |
| AUTH_USERNAME_EXISTS | 409 | 用户名已存在 |
| AUTH_PASSWORD_MISMATCH | 400 | 密码不一致 |
| VIDEO_NOT_FOUND | 404 | 视频不存在 |
| VIDEO_NOT_APPROVED | 403 | 视频未审核通过 |
| VIDEO_ALREADY_LIKED | 409 | 已点赞 |
| USER_ALREADY_FOLLOWING | 409 | 已关注 |
| USER_CANNOT_FOLLOW_SELF | 400 | 不能关注自己 |
| TASK_NOT_CLAIMABLE | 400 | 任务不可领取 |

## 6. 中间件

```
请求 → Recovery → RequestID → Logger → BodyLimit → Timeout → CORS → RateLimiter → Auth(可选) → Handler
```

| 中间件 | 职责 |
|--------|------|
| Recovery | panic 恢复 + stack trace 日志 |
| RequestID | 生成 X-Request-ID 贯穿日志 |
| Logger | slog 结构化请求日志 |
| BodyLimit | 请求体大小限制，默认 4MB（视频走预签名不经过服务器） |
| Timeout | 请求超时 30s，超时返回 503 |
| CORS | 跨域 |
| RateLimiter | 基于 Redis 令牌桶；全局 100 req/s，登录/注册 5 req/min/IP |
| Auth | JWT 验证，三档：必须/可选/无 |

### Auth 中间件三档

- **必须**：无 token 或无效 → 401
- **可选**：有 token 解析，无 token 放行
- **无**：不挂 auth 中间件

## 7. 认证架构

### 7.1 Token 结构

**Access Token**（15min）：
```json
{
  "header": { "alg": "HS256", "kid": "key-2026-02" },
  "payload": {
    "jti": "uuid-v4",
    "iss": "gabon-service",
    "aud": "customer",
    "sub": "customer_id",
    "token_type": "access",
    "family_id": "uuid-v4",
    "role": "customer",
    "exp": 1708300000,
    "iat": 1708299100
  }
}
```

**Refresh Token**（7d）：
```json
{
  "header": { "alg": "HS256", "kid": "key-2026-02" },
  "payload": {
    "jti": "uuid-v4",
    "iss": "gabon-service",
    "aud": "customer",
    "sub": "customer_id",
    "token_type": "refresh",
    "family_id": "uuid-v4",
    "exp": 1708900000,
    "iat": 1708299100
  }
}
```

管理员 token 使用 `"iss": "gabon-admin"`, `"aud": "admin"`，中间件校验 iss/aud 防止 token 混用。

**admin 与 customer 共用同一套 token 机制**：admin token 同样包含 `jti`、`family_id`、`token_type`，
登出时同样执行黑名单 + family 吊销。两端的区别仅在 iss/aud/签名密钥，业务逻辑复用同一套 JWT service。

### 7.2 域隔离

| 字段 | 客户端 token | 管理员 token |
|------|-------------|-------------|
| iss | gabon-service | gabon-admin |
| aud | customer | admin |
| 签名密钥 | JWT_CUSTOMER_SECRET | JWT_ADMIN_SECRET |

中间件校验时必须同时验证 iss + aud + 对应密钥，三者任一不匹配即 401。

### 7.3 登出（黑名单 + Family 吊销）

登出时执行两步（access token payload 包含 family_id，可直接提取）：
1. 将 access token 的 `jti` 写入 Redis 黑名单：
   - Key: `token:blacklist:{jti}`
   - TTL: access token 剩余有效期
   - Auth 中间件每次校验时查黑名单
2. 从 access token claims 中提取 `family_id`，删除 refresh family：
   - `DEL token:family:{family_id}`
   - 攻击者即使持有旧 refresh token 也无法换新 access

### 7.4 Refresh Token 防重放（Token Family）

每次登录生成一个 `family_id`，refresh token 包含此 family_id。

**Redis 存储**：
- Key: `token:family:{family_id}`
- Value: `{ "current_jti": "最新 refresh token 的 jti", "customer_id": 123 }`
- TTL: 7d（和 refresh token 同生命周期）

**刷新流程**（先签发，再原子 CAS）：
1. 验证 refresh token 签名和有效期
2. **先生成**新 access token + 新 refresh token（含 new_jti），暂不返回客户端
3. 执行 Redis Lua 脚本（原子 CAS），通过 KEYS/ARGV 传参：
   ```lua
   -- KEYS[1] = "token:family:{family_id}"
   -- ARGV[1] = expected_jti (当前 refresh token 的 jti)
   -- ARGV[2] = new_jti (新 refresh token 的 jti)
   local current = redis.call("GET", KEYS[1])
   if not current then return -1 end           -- family 不存在
   local data = cjson.decode(current)
   if data.current_jti ~= ARGV[1] then
       redis.call("DEL", KEYS[1])              -- 重放攻击，吊销 family
       return -2
   end
   data.current_jti = ARGV[2]
   redis.call("SET", KEYS[1], cjson.encode(data), "KEEPTTL")
   return 0                                     -- CAS 成功
   ```
   Go 调用示例：`rdb.EvalSha(ctx, sha, []string{familyKey}, oldJti, newJti)`
4. Lua 返回 0 → 返回步骤 2 预生成的新 tokens 给客户端
5. Lua 返回 -1 → family 已过期或被吊销，返回 401
6. Lua 返回 -2 → 重放攻击，返回 401，用户需重新登录

**为什么先签发再 CAS**：如果先 CAS 再签发，签发失败会导致旧 token 已作废但客户端拿不到新 token。
先签发的代价仅是 CAS 失败时丢弃预生成的 token，无副作用。

**已知 trade-off：严格重放检测 vs 网络重试**：
客户端网络抖动导致的重试会被误判为重放，触发 family 吊销，用户被迫重新登录。
这是刻意的安全取舍——优先防盗，接受极端场景下的重登体验。
客户端应实现：refresh 返回 401 时自动跳转登录页，不做盲目重试。

### 7.5 密钥轮换

- JWT header 包含 `kid`（如 `key-2026-02`）
- 配置中维护密钥映射：`{ "key-2026-02": "secret-xxx", "key-2026-01": "old-secret" }`
- 签发用最新 kid，验证时按 kid 查找对应密钥
- 旧密钥保留至少一个 refresh token 生命周期（7d）后可移除

## 8. 测试策略

| 层级 | 工具 | 覆盖 |
|------|------|------|
| Repository | testify + 真实 PG | SQL 查询正确性 |
| Service | testify + mock repo (interface) | 业务逻辑 |
| Transport | httptest + mock service | HTTP 状态码、请求解析 |
| 集成 | 完整启动 + 真实 DB | 端到端关键路径 |

**必须覆盖的并发/安全测试**：
- 并发 refresh：同一旧 token 并发请求，仅一个成功，其余返回 401
- logout 后 refresh 失效：登出后用旧 refresh token 刷新必须 401（family 已删除）
- 并发点赞：同一用户对同一视频并发点赞，like_count 只增 1
- 并发任务领取：同一任务并发 claim，仅一次成功加钻石

**测试门禁**：以上并发/安全测试必须在实现阶段编写并通过，作为 CI 门禁，不可跳过或推迟到"质量阶段"。

## 9. 项目结构

```
gabon-go/
├── cmd/api/main.go
├── internal/
│   ├── transport/
│   │   ├── router.go
│   │   ├── response.go        # 统一响应 + 错误转换
│   │   ├── auth_handler.go
│   │   ├── user_handler.go
│   │   ├── video_handler.go
│   │   ├── task_handler.go
│   │   ├── admin_handler.go
│   │   └── middleware/
│   │       ├── jwt.go
│   │       ├── logger.go
│   │       └── recovery.go
│   ├── service/
│   │   ├── auth.go
│   │   ├── user.go
│   │   ├── video.go
│   │   └── task.go
│   ├── repository/            # sqlc 生成
│   ├── model/
│   │   ├── errors.go          # AppError + ErrorCode
│   │   ├── auth.go
│   │   ├── user.go
│   │   ├── video.go
│   │   └── task.go
│   └── config/
│       └── config.go
├── db/
│   ├── migrations/
│   │   └── 001_init.sql
│   └── queries/
│       ├── customer.sql
│       ├── video.sql
│       ├── user_follow.sql
│       ├── video_like.sql
│       ├── video_play_record.sql
│       ├── task_definition.sql
│       ├── task_progress.sql
│       └── admin_user.sql
├── sqlc.yaml
├── .golangci.yml
├── .env.example
├── Makefile
├── go.mod
└── docs/plans/
```

## 10. 实现分步

| Step | 范围 | 产出 |
|------|------|------|
| 1a | 项目骨架 | go.mod, config, Makefile, .env, golangci, 统一响应 |
| 1b | 数据库 | migration, sqlc 配置, queries, 生成代码 |
| 1c | 认证 | service + handler + JWT 中间件 |
| 2 | 用户 | 资料 CRUD + 关注/粉丝 |
| 3 | 视频 | 上传(mock) + 列表 + 详情 + 点赞 + 播放记录 |
| 4 | 任务 | 定义 + 进度 + 领取奖励 |
| 5 | 后台 | 管理员 CRUD + 视频审核 + 报表 |
| 6 | 质量 | 补全测试 + OpenAPI 文档 + CI 配置 |
