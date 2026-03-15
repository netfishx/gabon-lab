# 集成测试设计文档

> gabon-go 集成测试：testcontainers-go + httptest，覆盖 4 个 CI 门禁并发测试 + 8 个关键路径端到端测试。

## 1. 技术选型

| 职责 | 选择 | 说明 |
|------|------|------|
| 数据库容器 | testcontainers-go + postgres:16-alpine | 自动启动/销毁，CI 零配置 |
| Redis 容器 | testcontainers-go + redis:7-alpine | token 黑名单 + refresh family |
| HTTP 测试 | httptest.Server | 真实 Echo+Huma 服务器，覆盖中间件全链路 |
| 数据库迁移 | goose（程序内调用） | 复用 db/migrations/ |
| Storage | Stub 实现 | Upload 返回固定 URL，见 §9 trade-off 说明 |

## 2. 文件结构

```
tests/
└── integration/
    ├── testutil/
    │   └── setup.go       # TestMain helper：容器、迁移、依赖组装、httptest 服务器
    ├── auth_test.go        # 认证流程 + 并发 refresh + logout 后 refresh
    ├── video_test.go       # 视频 CRUD + 并发点赞
    ├── task_test.go        # 任务完成 + 并发领取
    └── user_test.go        # 关注/取关 + 资料更新
```

所有文件顶部加 `//go:build integration`，`make test` 不触发。

## 3. 容器生命周期

```
TestMain(m *testing.M)
  ├── testcontainers 启动 PG 容器
  ├── testcontainers 启动 Redis 容器
  ├── goose 跑 db/migrations/ 建表
  ├── 构造依赖链（复用 main.go 组装逻辑）
  │   └── StorageService 用 stub 替代
  ├── 启动 httptest.Server
  ├── m.Run()
  └── 容器自动销毁（cleanup 函数）
```

package 级共享一套容器，所有测试串行运行。

## 4. 测试辅助工具 (`testutil/setup.go`)

### TestEnv

```go
type TestEnv struct {
    Server   *httptest.Server
    DB       *pgxpool.Pool
    Redis    *redis.Client
    JWTSvc   *service.JWTService
}
```

### Helper 方法

| 方法 | 用途 |
|------|------|
| `Setup() (*TestEnv, func())` | TestMain 调用，返回环境和 cleanup |
| `RegisterUser(t, username, password) AuthTokens` | 注册并返回 token |
| `LoginUser(t, username, password) AuthTokens` | 登录并返回 token |
| `InsertVideo(t, customerID, title) int64` | DB 直插已审核视频 |
| `InsertTaskDefinition(t, ...) int64` | DB 直插任务定义 |
| `Request(method, path, body, token) *http.Response` | HTTP 请求便捷方法 |

### 关键配置

- JWT TTL：access 60s, refresh 120s（足够 CI testcontainers 冷启动 + -race 检测）
- StorageService stub：Upload 返回固定 URL，Delete 返回 nil
- goose 迁移路径：`../../db/migrations`（相对于 tests/integration/）

## 5. 测试间隔离

- 每个测试用唯一用户名前缀（如 `test_auth_001_`），不依赖事务回滚
- 并发测试需要真实提交，不能用回滚隔离
- 每个测试自行创建 fixture，不共享状态

## 6. 测试用例

### 6.1 CI 门禁（4 个并发/安全测试）

| # | 文件 | 测试名 | 描述 |
|---|------|--------|------|
| 1 | auth_test.go | TestConcurrentRefresh | 10 goroutine 同时用同一 refresh token 刷新，恰好 1 成功 |
| 2 | auth_test.go | TestLogoutThenRefresh | logout 后用 refresh token 刷新，必须 401 |
| 3 | video_test.go | TestConcurrentLike | 10 用户同时点赞同一视频，like_count=10，无重复 |
| 4 | task_test.go | TestConcurrentClaim | 10 goroutine 同时 claim 同一 progress，恰好 1 成功 |

### 6.2 关键路径（8 个端到端测试）

| # | 文件 | 测试名 | 描述 |
|---|------|--------|------|
| 5 | auth_test.go | TestFullAuthLifecycle | 注册→登录→获取个人信息→修改密码→新密码登录 |
| 6 | auth_test.go | TestDuplicateRegister | 同一用户名注册两次 → 409 |
| 7 | video_test.go | TestVideoListAndLike | 视频列表→详情→点赞→确认 is_liked→取消点赞 |
| 8 | video_test.go | TestPlayRecords | 点击播放+有效播放→确认计数递增 |
| 9 | task_test.go | TestTaskProgressAndClaim | 任务进度推进→列表查询→正常领取→钻石增加 |
| 10 | user_test.go | TestFollowUnfollow | 关注→关注列表→粉丝列表→取消关注→列表为空 |
| 11 | user_test.go | TestFollowSelf | 自己关注自己 → 400 |
| 12 | user_test.go | TestUpdateProfile | 更新资料（name/phone/email/signature）→查询确认字段变更 |

## 7. CI 集成

### 命令

```bash
make test-integration   # go test -v -race -count=1 -tags=integration -timeout 5m ./tests/integration/...
```

### GitHub Actions

在 `.github/workflows/ci.yml` 追加 job：

```yaml
integration-test:
  runs-on: ubuntu-latest
  needs: [lint, test]
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-go@v5
      with:
        go-version: '1.26'
    - name: Integration tests
      run: make test-integration
```

CI 环境天然有 Docker，testcontainers 直接可用。

### 本地运行

- 前置条件：Docker Desktop 运行中
- 无需环境变量（PG/Redis URL 由 testcontainers 动态生成）
- 一条命令：`make test-integration`

## 8. 超时控制

| 层级 | 超时 |
|------|------|
| 容器启动 | 60s（testcontainers 默认） |
| 单个测试 | 30s（context.WithTimeout） |
| 整体 go test | 5m（-timeout 5m） |

## 9. 已知 Trade-off

### StorageService stub 不覆盖外部契约

集成测试用 stub 替代 Supabase Storage（Upload 返回固定 URL，Delete 返回 nil）。
这意味着 Supabase Storage API 的契约变更（URL 格式、鉴权方式、响应结构）不会被集成测试捕获。

**接受理由**：
- StorageService 是薄 HTTP 客户端（80 行），逻辑极简
- 集成测试目标是验证业务逻辑（并发安全、认证链路、数据一致性），不是外部 API 契约
- 测真实 Storage 需要 Supabase 实例或 S3 兼容 mock，引入的复杂度与收益不匹配

**缓解措施**：如未来需要验证 Storage 契约，可单独写一个 contract test 直连 Supabase staging 环境。
