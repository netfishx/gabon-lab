# Modern Java Implementation Design — gabon-lab

## Overview

gabon-lab 短视频平台后端对比实验室新增现代 Java 实现。使用 Spring Boot 4.0 + JDK 25 作为"正统 Java 生态"代表，与 Go (Echo)、Rust (Axum)、Kotlin (Ktor) 三端对齐业务 API，替换旧 Java (Spring Boot 3.2 + MySQL + JDK 17) 的评测数据。

## Goals

1. **正统 Java 代表**：展示 Spring Boot 4.0 + Virtual Threads + 现代 Java 语言特性的生产级面貌
2. **替换旧评测数据**：用新技术栈重跑 benchmark，更新 `docs/benchmark-report.md` 中 Java 列
3. **四端 DX 对齐**：lint、migration、test、build 命令与 Go/Rust/Kotlin 一致

## Non-Goals

- 不做微服务拆分（单体，与其他三端一致）
- 不用 Spring Security（手动 JWT，公平对比）
- 不用 Lombok（JDK 25 有 Record）
- 不用 Hibernate/JPA（用 Spring Data JDBC，显式 SQL）

## Tech Stack

| 维度 | 选型 | 理由 |
|------|------|------|
| 框架 | Spring Boot 4.0.3 + Spring Framework 7.0 | 正统 Java 生态，2025.11 发布 |
| JDK | 25 (LTS, 2025.09) | 最新 LTS，Spring Boot 4.x 最低要求 JDK 17，推荐 25。Virtual Threads GA，Record/Sealed/Pattern Matching |
| 数据库 | PostgreSQL 18（共享） | 统一基础设施，公平对比 |
| 数据访问 | Spring Data JDBC | 显式 SQL，无 Hibernate 魔法，@Query 手写 |
| 迁移 | Flyway（启动自动 + Gradle 插件手动） | 与 Kotlin 实现一致 |
| 缓存 | Spring Data Redis（Lettuce） | Spring 生态默认 Redis 客户端 |
| 认证 | com.auth0:java-jwt | 与 Kotlin 实现统一，手动 JWT |
| 存储 | AWS SDK v2 (S3) | 与 Go/Rust 对齐 |
| 构建 | Gradle Kotlin DSL | 与 Kotlin 实现一致，增量编译快 |
| Lint | Spotless + google-java-format | 补齐 Java lint 短板 |
| 测试 | JUnit 5 + Mockito + Testcontainers | Spring Boot 标配 |
| 并发 | Virtual Threads | `spring.threads.virtual.enabled=true` 一键开启 |

## Project Structure

```
java/
├── build.gradle.kts
├── settings.gradle.kts
├── Dockerfile
├── src/main/
│   ├── java/lab/gabon/
│   │   ├── GabonApplication.java
│   │   ├── config/
│   │   │   ├── AppConfig.java          # S3, JWT domain 配置
│   │   │   ├── WebConfig.java          # Filter 注册、CORS
│   │   │   └── RedisConfig.java        # Redis 连接配置
│   │   ├── controller/
│   │   │   ├── api/                    # 客户端 Controller
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── VideoController.java
│   │   │   │   ├── UserController.java
│   │   │   │   ├── TaskController.java
│   │   │   │   └── ActivityController.java
│   │   │   └── admin/                  # 管理端 Controller
│   │   │       ├── AdminAuthController.java
│   │   │       ├── AdminUserController.java
│   │   │       ├── CustomerController.java
│   │   │       ├── VideoReviewController.java
│   │   │       └── ReportController.java
│   │   ├── service/
│   │   │   ├── AuthService.java
│   │   │   ├── VideoService.java
│   │   │   ├── UserService.java
│   │   │   ├── TaskService.java
│   │   │   ├── ActivityService.java
│   │   │   ├── JwtService.java
│   │   │   ├── TokenStore.java         # Redis refresh token 管理
│   │   │   └── StorageService.java     # S3 presigned URL
│   │   ├── repository/
│   │   │   ├── CustomerRepository.java
│   │   │   ├── VideoRepository.java
│   │   │   ├── VideoLikeRepository.java
│   │   │   ├── VideoPlayRecordRepository.java
│   │   │   ├── UserFollowRepository.java
│   │   │   ├── AdminUserRepository.java
│   │   │   ├── TaskDefinitionRepository.java
│   │   │   ├── TaskProgressRepository.java
│   │   │   └── SignInRecordRepository.java
│   │   ├── model/
│   │   │   ├── entity/                 # 数据库映射实体
│   │   │   │   ├── Customer.java
│   │   │   │   ├── Video.java
│   │   │   │   ├── AdminUser.java
│   │   │   │   └── ...
│   │   │   ├── request/                # Request Records
│   │   │   │   ├── RegisterRequest.java
│   │   │   │   ├── LoginRequest.java
│   │   │   │   └── ...
│   │   │   └── response/              # Response Records
│   │   │       ├── AuthResponse.java
│   │   │       ├── VideoDetailResponse.java
│   │   │       └── ...
│   │   ├── security/
│   │   │   ├── JwtAuthFilter.java      # OncePerRequestFilter
│   │   │   └── JwtDomain.java          # Record: 双域 JWT 配置
│   │   └── common/
│   │       ├── ApiResponse.java        # 统一响应 Record
│   │       ├── AppError.java           # Sealed Interface 错误层级
│   │       ├── AppException.java       # RuntimeException 包装
│   │       ├── GlobalExceptionHandler.java  # @RestControllerAdvice
│   │       └── PageResponse.java       # 分页响应 Record
│   └── resources/
│       ├── application.yml
│       └── db/migration/
│           └── V001__init.sql          # 复用 PG schema
└── src/test/java/lab/gabon/
    ├── service/                        # 单元测试 (Mockito)
    ├── controller/                     # 集成测试 (Testcontainers)
    └── repository/                     # Repository 集成测试
```

## Architecture

### Three-Layer Pattern

```
@RestController (controller/)     参数解析、校验、调用 service、映射响应
        ↓
@Service (service/)               纯业务逻辑，无框架依赖
        ↓
Repository (repository/)          Spring Data JDBC 接口 + @Query 自定义 SQL
        ↓
PostgreSQL + Redis
```

- Controller 薄层：参数绑定 + 调用 service + 返回 ApiResponse
- Service 纯 Java：构造器注入，不依赖 Spring 注解（除 @Service）
- Repository 显式 SQL：@Query 手写，原子操作，无 ORM 魔法

### Modern Java Patterns

**Response envelope（对齐 Go/Rust/Kotlin：int code）**：

成功：`{ "code": 0, "message": "ok", "data": {...} }`
错误：`{ "code": 401, "message": "invalid username or password", "data": null }`

```java
public record ApiResponse<T>(int code, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }
    public static ApiResponse<Void> error(AppError error) {
        return new ApiResponse<>(error.status(), error.message(), null);
    }
}
```

**Sealed Interface 错误层级**（对标 Kotlin sealed class / Rust thiserror）：

`errorCode()` 用于内部日志/调试，`status()` 和 `message()` 用于响应 envelope。

```java
public sealed interface AppError {
    String errorCode();  // 内部标识，如 "AUTH_INVALID_CREDENTIALS"
    String message();    // 人类可读，映射到 response.message
    int status();        // HTTP 状态码，映射到 response.code

    record InvalidCredentials() implements AppError {
        public String errorCode() { return "AUTH_INVALID_CREDENTIALS"; }
        public String message()   { return "invalid username or password"; }
        public int status()       { return 401; }
    }

    record NotFound(String resource) implements AppError {
        public String errorCode() { return "NOT_FOUND"; }
        public String message()   { return resource + " not found"; }
        public int status()       { return 404; }
    }
    // ... 19 error codes matching Go internal/model/errors.go
}
```

**Pattern Matching for switch**：用于 service 层业务分支处理。

**JSpecify @Nullable**：Spring Framework 7 原生支持，标注可空参数/返回值。

## Authentication

### Dual-Domain JWT Isolation

```
客户端 /api/v1/**    → CustomerJwtFilter → iss=gabon-service, aud=customer
管理端 /admin/v1/**  → AdminJwtFilter    → iss=gabon-admin,   aud=admin
```

- 手动实现，不用 Spring Security（公平对比）
- com.auth0:java-jwt 签发 + 验证
- JwtDomain Record 封装双域配置（issuer, audience, secret, TTL）
- FilterRegistrationBean 注册到 WebConfig，按 URL pattern 匹配
- 公开路径白名单：register, login, refresh

### Refresh Token — Redis Atomic CAS

- Lua 脚本原子校验旧 token + 写入新 token
- Token Family 检测重放攻击（同 Go 方案）

## Concurrency Safety

| 场景 | 策略 | SQL 模式 |
|------|------|----------|
| like_count 等计数器 | 原子 SQL | `SET col = col + 1` |
| 点赞/取消点赞 | CTE 原子操作 | INSERT/DELETE + UPDATE 在同一 CTE |
| 任务领取 | 行锁 | `SELECT ... FOR UPDATE` + 状态校验 |
| Refresh Token 轮换 | Redis Lua CAS | 原子 compare-and-swap |

## Rate Limiting

Redis 滑动窗口限流，按路由组区分策略（对齐 Go/Kotlin 实现）：

| 路由组 | 限流维度 | 速率 |
|--------|---------|------|
| Auth (`/auth/**`) | IP | 20 req/min |
| Public（未登录可访问） | IP | 120 req/min |
| User（需登录） | customer_id | 200 req/min |
| Admin（管理端） | admin_id | 200 req/min |

实现方式：自定义 `RateLimitFilter`（`OncePerRequestFilter`）+ Redis Lua 滑动窗口脚本。Health 端点 (`/actuator/health`) 豁免限流（benchmark 基准端点）。

## Design Constraints

### Username Case Handling

注册和登录统一用 `LOWER(username)` 查询。数据库唯一约束通过 partial index 实现：

```sql
CREATE UNIQUE INDEX idx_customers_username_active
    ON customers(LOWER(username)) WHERE deleted_at IS NULL;
```

Service 层所有 username 查询必须使用 `LOWER()` 函数，禁止直接 `WHERE username = ?`。

### Task System — Period Key

任务进度按 period_key 隔离，业务时区固定为 `Asia/Shanghai`：

| 任务周期 | period_key 格式 | 示例 |
|---------|----------------|------|
| 每日 | `yyyy-MM-dd` | `2026-03-19` |
| 每周 | `yyyy-'W'ww`（ISO 8601） | `2026-W12` |
| 每月 | `yyyy-MM` | `2026-03` |

Period Key 由 Service 层统一生成（`TaskService.generatePeriodKey()`），禁止各处自行格式化。JVM 启动时区不可依赖，必须显式使用 `ZoneId.of("Asia/Shanghai")`。

## API Endpoints

### Client API `/api/v1/`

| Module | Endpoint | Method |
|--------|----------|--------|
| Auth | `/auth/register` | POST |
| | `/auth/login` | POST |
| | `/auth/refresh` | POST |
| | `/auth/logout` | POST |
| | `/auth/me` | GET |
| | `/auth/password` | PUT |
| Users | `/users/me/profile` | GET / PUT |
| | `/users/me/avatar-upload-url` | POST |
| | `/users/{id}` | GET |
| | `/users/{id}/following` | GET |
| | `/users/{id}/followers` | GET |
| | `/users/{id}/follow` | POST / DELETE |
| Videos | `/videos` | GET |
| | `/videos/featured` | GET |
| | `/videos/me` | GET |
| | `/videos/upload-url` | POST |
| | `/videos/confirm-upload` | POST |
| | `/videos/{id}` | GET / DELETE |
| | `/videos/{id}/like` | POST / DELETE |
| | `/videos/{id}/play-click` | POST |
| | `/videos/{id}/play-valid` | POST |
| | `/users/{id}/videos` | GET |
| Tasks | `/tasks` | GET |
| | `/tasks/{progressId}/claim` | POST |
| Activity | `/activity/sign-in` | POST |

### Admin API `/admin/v1/`

| Module | Endpoint | Method |
|--------|----------|--------|
| Auth | `/auth/login` | POST |
| | `/auth/refresh` | POST |
| | `/auth/logout` | POST |
| | `/auth/me` | GET |
| Admin Users | `/admin-users` | GET / POST |
| | `/admin-users/{id}` | GET / PUT / DELETE |
| | `/admin-users/{id}/password` | PUT |
| Customers | `/customers` | GET |
| | `/customers/{id}/password` | PUT |
| Videos | `/videos` | GET |
| | `/videos/{id}` | GET / DELETE |
| | `/videos/{id}/review` | POST |
| Reports | `/reports/revenue` | GET |
| | `/reports/video/daily` | GET |
| | `/reports/video/summary` | GET |

~40 endpoints total, fully aligned with Go implementation.

## Database

- PostgreSQL 18 (shared with Go/Rust/Kotlin)
- Flyway migration: `V001__init.sql` reuses existing PG schema
- 8 core tables: admin_users, customers, videos, video_play_records, video_likes, user_follows, task_definitions, task_progress, customer_sign_in_records
- Dual mode: auto-migrate on startup + manual `make migrate-java`

## Testing

- **Unit tests**: JUnit 5 + Mockito — service layer, 100% business logic coverage
- **Integration tests**: Spring Boot Test + Testcontainers (PostgreSQL) — controller + repository
- **Commands**: `make test-java` → `./gradlew test`

### Required Concurrency Tests (CI Gate)

以下 4 个并发场景必须覆盖，与 Go CLAUDE.md 对齐：

1. **并发 Refresh Token**：多线程同时 refresh 同一 token，仅一个成功，其余失败
2. **Logout 后 Refresh 失效**：logout 后立即 refresh，必须返回错误
3. **并发点赞**：N 个线程同时对同一视频点赞，`like_count` 恰好增 1（不是 N）
4. **并发任务领取**：多线程同时 claim 同一 task_progress，仅一个成功

## Docker

Multi-stage build:
1. `eclipse-temurin:25-jdk-alpine` — build stage
2. `eclipse-temurin:25-jre-alpine` — runtime stage
3. Dependency caching layer for fast rebuilds

## Benchmark Integration

### New Files

| File | Purpose |
|------|---------|
| `bench/oha-java.sh` | Single-endpoint QPS benchmark |
| `bench/k6-java.js` | k6 scenario benchmark (full user flow) |

### Modified Files

| File | Change |
|------|--------|
| `bench/metrics.sh` | Add Java LOC, compile time, binary size, Docker image |
| `bench/correctness.sh` | Add Java port 8082 correctness checks |
| `docs/benchmark-report.md` | Replace old Java column with new data |
| `Makefile` | Add dev/build/test/lint/migrate-java commands |

### Expected Improvements Over Old Java

| Metric | Old (SB 3.2 + MySQL + JDK 17) | New (SB 4.0 + PG + JDK 25) |
|--------|-------------------------------|-------------------------------|
| QPS | 88K | ↑ (Virtual Threads + PG) |
| Memory | 354 MB | ↓ (Modular JARs + JRE 25) |
| Cold Start | 2,714 ms | ↓ (Spring Boot 4.0 startup optimizations) |
| Compile | 5.6s | ~comparable (Gradle) |

## Makefile Commands

```makefile
dev-java:       cd java && ./gradlew bootRun --no-daemon
build-java:     cd java && ./gradlew build --no-daemon
test-java:      cd java && ./gradlew test --no-daemon
lint-java:      cd java && ./gradlew spotlessCheck
migrate-java:   cd java && ./gradlew flywayMigrate

# Updated aggregate commands
migrate:        migrate-go migrate-rust migrate-java
bench-all:      bench-oha bench-k6-go bench-k6-rust bench-k6-kotlin bench-k6-java bench-metrics bench-correctness
```

## Port Assignment

| Service | Port |
|---------|------|
| Go | 8080 |
| Rust | 3000 |
| Kotlin | 8090 |
| **Java** | **8082** |

## Dependencies

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.auth0:java-jwt")
    implementation("software.amazon.awssdk:s3")
    implementation("org.springframework.security:spring-security-crypto") // bcrypt only

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
}
```

Note: `spring-security-crypto` is imported ONLY for BCrypt password hashing, not for the full Spring Security framework.

## Cross-Implementation Comparison

| Dimension | Go | Rust | Kotlin | **Java** |
|-----------|-----|------|--------|----------|
| Framework | Echo + Huma | Axum + Tower | Ktor 3.4 | **Spring Boot 4.0** |
| Runtime | Go 1.26 | Rust 1.94 | JDK 21 | **JDK 25** |
| DB Access | pgx + sqlc | SQLx | Exposed | **Spring Data JDBC** |
| Migration | goose | sqlx migrate | Flyway | **Flyway** |
| Cache | go-redis | deadpool-redis | Lettuce | **Lettuce (Spring Data Redis)** |
| Auth | golang-jwt | jsonwebtoken | java-jwt | **java-jwt** |
| Build | go build | cargo build | Gradle shadowJar | **Gradle bootJar** |
| Lint | golangci-lint | clippy | ktlint | **Spotless** |
| Test | testify | #[test] | JUnit 5 | **JUnit 5 + Testcontainers** |
| Concurrency | goroutine | tokio | coroutine + VT | **Virtual Threads** |
| DTO | struct | struct | data class | **Record** |
| Error Type | custom error | thiserror enum | sealed class | **Sealed Interface** |
| DI | manual | State<AppState> | manual | **Constructor Injection (Spring)** |
