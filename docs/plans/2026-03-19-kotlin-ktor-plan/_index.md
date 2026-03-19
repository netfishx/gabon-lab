# Kotlin/Ktor Implementation Plan

> 基于设计文档 `docs/plans/2026-03-19-kotlin-ktor-design/` 的实施计划。
> 25 个任务，覆盖 11 个 BDD Feature、~85 个 Gherkin 场景。

## Goal

在 gabon-lab 中新增 Kotlin/Ktor 实现（第四语言），使用 Ktor 3.4.0 + Kotlin 2.3.20 + Exposed 1.1.1 + JDK 21 Virtual Threads + ZGC，复用 Go/Rust 的 PostgreSQL schema，端口 8090，可运行全套 bench 脚本。

## Constraints

- 包名 `lab.gabon`，端口 8090，路由前缀 `/api/v1/` + `/admin/v1/`
- 响应格式 `JsonData<T>` (code:Int=0, message, data)，与 Go/Rust 对齐
- bcrypt cost=10（压测场景），报表路径 `/reports/video/daily`（与 Go 一致）
- 热路径（CTE 点赞、原子计数器）用 `exec()` 原生 SQL，常规 CRUD 用 Exposed DSL
- Test-first (Red-Green)：每个 feature 先写测试（Red），再实现（Green）

## Execution Plan

```yaml
tasks:
  # --- Infrastructure (Layer 0-1) ---
  - id: "001"
    subject: "Project scaffold and Gradle configuration"
    slug: "project-setup"
    type: "setup"
    depends-on: []
  - id: "002"
    subject: "Shared models and error handling"
    slug: "shared-models"
    type: "setup"
    depends-on: ["001"]
  - id: "003"
    subject: "Database infrastructure with Exposed and Virtual Threads"
    slug: "database-infra"
    type: "setup"
    depends-on: ["001"]
  - id: "004"
    subject: "Redis infrastructure with Lettuce coroutines"
    slug: "redis-infra"
    type: "setup"
    depends-on: ["001"]
  - id: "005"
    subject: "Dual-domain JWT service"
    slug: "jwt-service"
    type: "setup"
    depends-on: ["002"]
  - id: "006"
    subject: "S3 storage service with presigned URLs"
    slug: "storage-service"
    type: "setup"
    depends-on: ["002"]

  # --- Feature: Auth (BDD Features 1+2) ---
  - id: "007-test"
    subject: "Customer Auth + Token Security Test"
    slug: "auth-test"
    type: "test"
    depends-on: ["003", "004", "005"]
  - id: "007-impl"
    subject: "Customer Auth + Token Security Impl"
    slug: "auth-impl"
    type: "impl"
    depends-on: ["007-test"]

  # --- Feature: Video Management (BDD Feature 3) ---
  - id: "008-test"
    subject: "Video Management Test"
    slug: "video-test"
    type: "test"
    depends-on: ["003", "006", "007-impl"]
  - id: "008-impl"
    subject: "Video Management Impl"
    slug: "video-impl"
    type: "impl"
    depends-on: ["008-test"]

  # --- Feature: Like System (BDD Feature 4) ---
  - id: "009-test"
    subject: "Like System Test"
    slug: "like-test"
    type: "test"
    depends-on: ["008-impl"]
  - id: "009-impl"
    subject: "Like System Impl"
    slug: "like-impl"
    type: "impl"
    depends-on: ["009-test"]

  # --- Feature: Social System (BDD Feature 5) ---
  - id: "010-test"
    subject: "Social System Test"
    slug: "social-test"
    type: "test"
    depends-on: ["003", "007-impl"]
  - id: "010-impl"
    subject: "Social System Impl"
    slug: "social-impl"
    type: "impl"
    depends-on: ["010-test"]

  # --- Feature: User Profile (BDD Feature 10) ---
  - id: "011-test"
    subject: "User Profile Test"
    slug: "profile-test"
    type: "test"
    depends-on: ["003", "006", "007-impl"]
  - id: "011-impl"
    subject: "User Profile Impl"
    slug: "profile-impl"
    type: "impl"
    depends-on: ["011-test"]

  # --- Feature: Task System + Sign-In (BDD Features 6+7) ---
  - id: "012-test"
    subject: "Task System + Sign-In Test"
    slug: "task-test"
    type: "test"
    depends-on: ["003", "007-impl"]
  - id: "012-impl"
    subject: "Task System + Sign-In Impl"
    slug: "task-impl"
    type: "impl"
    depends-on: ["012-test"]

  # --- Feature: Admin (BDD Feature 8) ---
  - id: "013-test"
    subject: "Admin Video Review + CRUD Test"
    slug: "admin-test"
    type: "test"
    depends-on: ["003", "004", "005"]
  - id: "013-impl"
    subject: "Admin Video Review + CRUD Impl"
    slug: "admin-impl"
    type: "impl"
    depends-on: ["013-test"]

  # --- Feature: Admin Reports (BDD Feature 11) ---
  - id: "014-test"
    subject: "Admin Reports Test"
    slug: "report-test"
    type: "test"
    depends-on: ["013-impl"]
  - id: "014-impl"
    subject: "Admin Reports Impl"
    slug: "report-impl"
    type: "impl"
    depends-on: ["014-test"]

  # --- Feature: Rate Limiting (BDD Feature 9) ---
  - id: "015-test"
    subject: "Rate Limiting Test"
    slug: "ratelimit-test"
    type: "test"
    depends-on: ["004", "007-impl"]
  - id: "015-impl"
    subject: "Rate Limiting Impl"
    slug: "ratelimit-impl"
    type: "impl"
    depends-on: ["015-test"]

  # --- Integration ---
  - id: "016"
    subject: "Docker, Makefile, bench scripts integration"
    slug: "integration"
    type: "setup"
    depends-on: ["007-impl", "008-impl", "009-impl", "010-impl", "011-impl", "012-impl", "013-impl", "014-impl", "015-impl"]
```

## Task File References

### Infrastructure (Layer 0-1)
- [Task 001: Project Setup](./task-001-project-setup.md) — Gradle scaffold, deps, Application.kt
- [Task 002: Shared Models](./task-002-shared-models.md) — AppError, JsonData, Constants, StatusPages
- [Task 003: Database Infra](./task-003-database-infra.md) — AppConfig, HikariCP, Exposed Tables, dbQuery
- [Task 004: Redis Infra](./task-004-redis-infra.md) — Lettuce, TokenStore (blacklist + CAS)
- [Task 005: JWT Service](./task-005-jwt-service.md) — Dual-domain JWT, Authentication plugin
- [Task 006: Storage Service](./task-006-storage-service.md) — S3 presigned URLs, stub mode

### Features (Red-Green pairs)
- [Task 007: Auth Test](./task-007-auth-test.md) / [Auth Impl](./task-007-auth-impl.md) — Features 1+2 (23 scenarios)
- [Task 008: Video Test](./task-008-video-test.md) / [Video Impl](./task-008-video-impl.md) — Feature 3 (16 scenarios)
- [Task 009: Like Test](./task-009-like-test.md) / [Like Impl](./task-009-like-impl.md) — Feature 4 (8 scenarios)
- [Task 010: Social Test](./task-010-social-test.md) / [Social Impl](./task-010-social-impl.md) — Feature 5 (17 scenarios)
- [Task 011: Profile Test](./task-011-profile-test.md) / [Profile Impl](./task-011-profile-impl.md) — Feature 10 (6 scenarios)
- [Task 012: Task Test](./task-012-task-test.md) / [Task Impl](./task-012-task-impl.md) — Features 6+7 (17 scenarios)
- [Task 013: Admin Test](./task-013-admin-test.md) / [Admin Impl](./task-013-admin-impl.md) — Feature 8 (19 scenarios)
- [Task 014: Report Test](./task-014-report-test.md) / [Report Impl](./task-014-report-impl.md) — Feature 11 (3 scenarios)
- [Task 015: Rate Limit Test](./task-015-ratelimit-test.md) / [Rate Limit Impl](./task-015-ratelimit-impl.md) — Feature 9 (5 scenarios)

### Integration
- [Task 016: Integration](./task-016-integration.md) — Dockerfile, Makefile, bench scripts

## BDD Coverage

| BDD Feature | Task Pair | Scenarios |
|-------------|-----------|-----------|
| Feature 1: Customer Authentication | 007 | 16 |
| Feature 2: Token Refresh and Security | 007 | 7 |
| Feature 3: Video Management | 008 | 16 |
| Feature 4: Like System | 009 | 8 |
| Feature 5: Social System | 010 | 17 |
| Feature 6: Task System | 012 | 12 |
| Feature 7: Daily Sign-In | 012 | 5 |
| Feature 8: Admin Video Review | 013 | 17 |
| Feature 9: Rate Limiting | 015 | 5 |
| Feature 10: User Profile | 011 | 6 |
| Feature 11: Admin Reports | 014 | 3 |
| **Total** | | **~112** |

## Dependency Chain

```
001 (project-setup)
├── 002 (shared-models)
│   ├── 005 (jwt-service)
│   └── 006 (storage-service)
├── 003 (database-infra)
└── 004 (redis-infra)

005 + 003 + 004 ──► 007-test ──► 007-impl (auth)
                                    │
        ┌───────────────────────────┤
        │                           │
003 + 006 + 007-impl ──► 008-test ──► 008-impl (video)
        │                               │
        │                    008-impl ──► 009-test ──► 009-impl (like)
        │
003 + 007-impl ──► 010-test ──► 010-impl (social)
        │
003 + 006 + 007-impl ──► 011-test ──► 011-impl (profile)
        │
003 + 007-impl ──► 012-test ──► 012-impl (task/sign-in)

005 + 003 + 004 ──► 013-test ──► 013-impl (admin)
                                    │
                    013-impl ──► 014-test ──► 014-impl (reports)

004 + 007-impl ──► 015-test ──► 015-impl (rate-limit)

all impl tasks ──► 016 (integration)
```

### Parallelizable groups

After 001 completes, the following can run in parallel:
- **Group A**: 002 → 005, 006
- **Group B**: 003
- **Group C**: 004

After infra (001-006) completes:
- **Group D** (parallel): 007, 013 (both only need DB+Redis+JWT)
- After 007-impl: **Group E** (parallel): 008, 010, 011, 012, 015
- After 008-impl: 009
- After 013-impl: 014
- After all impl: 016
