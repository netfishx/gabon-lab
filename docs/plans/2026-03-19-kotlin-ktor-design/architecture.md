# Kotlin (Ktor) Implementation Architecture

> gabon-lab 第四语言实现：Kotlin + Ktor，复用 Go/Rust 的 PostgreSQL schema，对标同一业务 API。

## 1. 目录结构

单 Gradle 模块，扁平包结构（与 Go/Rust 保持一致，不学 Java 的多模块）：

```
kotlin/
├── build.gradle.kts              # 依赖 + 插件配置
├── settings.gradle.kts           # 项目名
├── gradle.properties             # JVM 参数、版本号集中管理
├── gradle/
│   └── libs.versions.toml        # Gradle Version Catalog
├── Dockerfile
├── Makefile
├── CLAUDE.md
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── lab/gabon/
│   │   │       ├── Application.kt          # 入口：Ktor embeddedServer + 依赖组装
│   │   │       │
│   │   │       ├── config/
│   │   │       │   └── AppConfig.kt         # 环境变量加载（对标 Go config.go）
│   │   │       │
│   │   │       ├── model/
│   │   │       │   ├── Constants.kt          # 枚举常量（VideoStatus, TaskType 等）
│   │   │       │   ├── Error.kt              # sealed interface AppError + ErrorCode
│   │   │       │   ├── Request.kt            # 请求 DTO（@Serializable）
│   │   │       │   └── Response.kt           # 统一响应 JsonData<T> + Paginated<T>
│   │   │       │
│   │   │       ├── repository/
│   │   │       │   ├── Tables.kt             # Exposed DSL 表定义（全部表）
│   │   │       │   ├── CustomerRepo.kt       # 客户 CRUD
│   │   │       │   ├── AdminUserRepo.kt      # 管理员 CRUD
│   │   │       │   ├── VideoRepo.kt          # 视频 + 点赞
│   │   │       │   ├── PlayRecordRepo.kt     # 播放记录
│   │   │       │   ├── SocialRepo.kt         # 关注/粉丝
│   │   │       │   ├── TaskRepo.kt           # 任务定义 + 进度
│   │   │       │   ├── SignInRepo.kt         # 签到记录
│   │   │       │   └── ReportRepo.kt         # 统计报表
│   │   │       │
│   │   │       ├── service/
│   │   │       │   ├── JwtService.kt         # 双域 JWT 签发/解析
│   │   │       │   ├── TokenStore.kt         # Redis 黑名单 + Family CAS
│   │   │       │   ├── AuthService.kt        # 客户注册/登录/刷新/登出
│   │   │       │   ├── AdminService.kt       # 管理员认证 + CRUD
│   │   │       │   ├── UserService.kt        # 用户资料 + 头像
│   │   │       │   ├── VideoService.kt       # 视频管理 + 审核
│   │   │       │   ├── SocialService.kt      # 关注/取关
│   │   │       │   ├── TaskService.kt        # 任务 + 签到
│   │   │       │   ├── StorageService.kt     # S3 presign + 公共 URL
│   │   │       │   └── ReportService.kt      # 统计报表
│   │   │       │
│   │   │       ├── route/
│   │   │       │   ├── AuthRoutes.kt         # /api/v1/auth/*
│   │   │       │   ├── UserRoutes.kt         # /api/v1/users/*
│   │   │       │   ├── VideoRoutes.kt        # /api/v1/videos/*
│   │   │       │   ├── SocialRoutes.kt       # /api/v1/customer/*
│   │   │       │   ├── TaskRoutes.kt         # /api/v1/tasks/*, /api/v1/activity/*
│   │   │       │   ├── AdminRoutes.kt        # /admin/v1/*
│   │   │       │   └── ReportRoutes.kt       # /admin/v1/reports/*
│   │   │       │
│   │   │       └── plugin/
│   │   │           ├── Routing.kt            # 汇总注册所有路由
│   │   │           ├── Serialization.kt      # ContentNegotiation + JSON
│   │   │           ├── Authentication.kt     # JWT 双域配置
│   │   │           ├── RateLimit.kt          # Redis 滑动窗口限流
│   │   │           ├── ErrorHandling.kt      # StatusPages 异常映射
│   │   │           └── Monitoring.kt         # CORS + Compression + CallTimeout + CallLogging
│   │   │
│   │   └── resources/
│   │       └── logback.xml                   # SLF4J/Logback 配置
│   │
│   └── test/
│       └── kotlin/
│           └── lab/gabon/
│               ├── service/
│               │   ├── JwtServiceTest.kt
│               │   ├── AuthServiceTest.kt
│               │   └── TaskServiceTest.kt
│               ├── route/
│               │   ├── AuthRoutesTest.kt
│               │   └── VideoRoutesTest.kt
│               └── TestUtil.kt               # testApplication 辅助
```

### 三层映射关系

| 层 | Kotlin | Go | Rust |
|----|--------|----|------|
| HTTP 入口 | `route/` | `transport/` | `crates/api/src/` |
| 业务逻辑 | `service/` | `service/` | `crates/domain/src/` |
| 数据访问 | `repository/` | `repository/` | `crates/infra/src/` |
| 公共模型 | `model/` | `model/` | `crates/shared/src/` |
| 配置 | `config/` | `config/` | `crates/shared/src/config.rs` |
| 中间件 | `plugin/` | `transport/middleware/` | `crates/api/src/middleware.rs` |

## 2. 依赖清单

### gradle/libs.versions.toml

```toml
[versions]
kotlin = "2.3.20"
ktor = "3.4.0"
exposed = "1.1.1"
hikari = "6.3.0"
postgresql = "42.7.7"
kotlinx-serialization = "1.10.0"
kotlinx-datetime = "0.6.2"
logback = "1.5.18"
bcrypt = "0.10.2"
java-jwt = "4.5.0"       # com.auth0:java-jwt（Ktor 官方 JWT 依赖的底层库）
lettuce = "7.3.0.RELEASE"
aws-kotlin = "1.5.5"
kotlinx-coroutines = "1.10.2"
mockk = "1.14.2"
kotlin-test = "2.3.20"

[libraries]
# Ktor Server
ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
ktor-server-netty = { module = "io.ktor:ktor-server-netty", version.ref = "ktor" }
ktor-server-content-negotiation = { module = "io.ktor:ktor-server-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-server-auth = { module = "io.ktor:ktor-server-auth", version.ref = "ktor" }
ktor-server-auth-jwt = { module = "io.ktor:ktor-server-auth-jwt", version.ref = "ktor" }
ktor-server-status-pages = { module = "io.ktor:ktor-server-status-pages", version.ref = "ktor" }
ktor-server-cors = { module = "io.ktor:ktor-server-cors", version.ref = "ktor" }
ktor-server-compression = { module = "io.ktor:ktor-server-compression", version.ref = "ktor" }
ktor-server-call-logging = { module = "io.ktor:ktor-server-call-logging", version.ref = "ktor" }
ktor-server-call-id = { module = "io.ktor:ktor-server-call-id", version.ref = "ktor" }
ktor-server-request-validation = { module = "io.ktor:ktor-server-request-validation", version.ref = "ktor" }

# Ktor Test
ktor-server-test-host = { module = "io.ktor:ktor-server-test-host", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }

# Database
exposed-core = { module = "org.jetbrains.exposed:exposed-core", version.ref = "exposed" }
exposed-jdbc = { module = "org.jetbrains.exposed:exposed-jdbc", version.ref = "exposed" }
exposed-kotlin-datetime = { module = "org.jetbrains.exposed:exposed-kotlin-datetime", version.ref = "exposed" }
hikari = { module = "com.zaxxer:HikariCP", version.ref = "hikari" }
postgresql = { module = "org.postgresql:postgresql", version.ref = "postgresql" }

# Serialization
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }

# Auth
bcrypt = { module = "at.favre.lib:bcrypt", version.ref = "bcrypt" }

# Redis
lettuce = { module = "io.lettuce:lettuce-core", version.ref = "lettuce" }

# S3
aws-s3 = { module = "aws.sdk.kotlin:s3", version.ref = "aws-kotlin" }

# Coroutines
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-jdk8 = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-jdk8", version.ref = "kotlinx-coroutines" }

# Logging
logback = { module = "ch.qos.logback:logback-classic", version.ref = "logback" }

# Testing
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin-test" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ktor = { id = "io.ktor.plugin", version.ref = "ktor" }
```

### build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    application
}

group = "lab.gabon"
version = "0.1.0"

application {
    mainClass.set("lab.gabon.ApplicationKt")

    // Profile 1: Virtual Threads as default carrier for coroutines
    applicationDefaultJvmArgs = listOf(
        "--enable-preview",
        "-XX:+UseZGC",
        "-XX:+ZGenerational",
        "-Xmx256m",
        "-Xms128m",
    )
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters")
    }
}

dependencies {
    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.request.validation)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.hikari)
    implementation(libs.postgresql)

    // Serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Auth
    implementation(libs.bcrypt)

    // Redis
    implementation(libs.lettuce)

    // S3
    implementation(libs.aws.s3)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.jdk8)

    // Logging
    implementation(libs.logback)

    // Testing
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
}

ktor {
    fatJar {
        archiveFileName.set("gabon-api.jar")
    }
}
```

### gradle.properties

```properties
kotlin.code.style=official
org.gradle.jvmargs=-Xmx1024m
org.gradle.parallel=true
org.gradle.caching=true
kotlin.daemon.jvmargs=-Xmx1024m
```

## 3. 端口分配

| 服务 | 端口 | API 前缀 |
|------|------|---------|
| Go | 8080 | `/api/v1/`, `/admin/v1/` |
| Java | 8082 | `/service/api/` |
| Rust | 3000 | `/api/`, `/admin/` |
| **Kotlin** | **8090** | **`/api/v1/`, `/admin/v1/`** |

选择 8090：不与现有任何服务冲突，与 Go 采用相同的路由前缀风格（带版本号）。

在 `.env.example` 中新增：
```bash
# Kotlin server port
KOTLIN_PORT=8090
```

## 4. 配置加载

### 4.1 .env 文件加载策略

Kotlin 没有 dotenvy 这样的社区标准库。采用与 Go 相同的策略：运行时从 `../.env` 读取环境变量。

```kotlin
// config/AppConfig.kt

/**
 * 启动时调用一次，从 ../.env 加载环境变量到 System properties。
 * 已存在的环境变量不被覆盖（系统环境变量优先）。
 */
fun loadDotEnv() {
    val envFile = Path("../.env")
    if (!envFile.exists()) return
    envFile.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") && '=' in it }
        .forEach { line ->
            val (key, value) = line.split('=', limit = 2)
            if (System.getenv(key.trim()) == null) {
                System.setProperty(key.trim(), value.trim())
            }
        }
}

/** 读取环境变量，fallback 到 System property（由 loadDotEnv 填充）。 */
private fun env(key: String): String? =
    System.getenv(key) ?: System.getProperty(key)

private fun envRequired(key: String): String =
    env(key) ?: error("$key must be set")

private fun envOrDefault(key: String, default: String): String =
    env(key) ?: default

private fun envOrInt(key: String, default: Int): Int =
    env(key)?.toIntOrNull() ?: default
```

### 4.2 AppConfig 数据类

```kotlin
data class AppConfig(
    val port: Int,
    val databaseUrl: String,
    val redisUrl: String,
    val jwt: JwtConfig,
    val s3: S3Config,
) {
    companion object {
        fun fromEnv(): AppConfig {
            loadDotEnv()
            return AppConfig(
                port = envOrInt("KOTLIN_PORT", 8090),
                databaseUrl = envRequired("DATABASE_URL"),
                redisUrl = envOrDefault("REDIS_URL", "redis://localhost:6379/0"),
                jwt = JwtConfig(
                    customerSecret = envRequired("JWT_CUSTOMER_SECRET"),
                    customerAccessTtl = envDuration("JWT_CUSTOMER_ACCESS_TTL", 15.minutes),
                    customerRefreshTtl = envDuration("JWT_CUSTOMER_REFRESH_TTL", 168.hours),
                    adminSecret = envRequired("JWT_ADMIN_SECRET"),
                    adminAccessTtl = envDuration("JWT_ADMIN_ACCESS_TTL", 15.minutes),
                    adminRefreshTtl = envDuration("JWT_ADMIN_REFRESH_TTL", 168.hours),
                    currentKid = envOrDefault("JWT_CURRENT_KID", "key-2026-03"),
                ),
                s3 = S3Config(
                    endpoint = envOrDefault("S3_ENDPOINT", ""),
                    region = envOrDefault("S3_REGION", "garage"),
                    accessKey = envOrDefault("S3_ACCESS_KEY", ""),
                    secretKey = envOrDefault("S3_SECRET_KEY", ""),
                    bucketVideos = envOrDefault("S3_BUCKET_VIDEOS", "gabon-videos"),
                    bucketAvatars = envOrDefault("S3_BUCKET_AVATARS", "gabon-avatars"),
                ),
            )
        }
    }
}

data class JwtConfig(
    val customerSecret: String,
    val customerAccessTtl: Duration,
    val customerRefreshTtl: Duration,
    val adminSecret: String,
    val adminAccessTtl: Duration,
    val adminRefreshTtl: Duration,
    val currentKid: String,
)

data class S3Config(
    val endpoint: String,
    val region: String,
    val accessKey: String,
    val secretKey: String,
    val bucketVideos: String,
    val bucketAvatars: String,
) {
    val isConfigured: Boolean get() = endpoint.isNotBlank()
}
```

## 5. 数据库 Schema

### 5.1 复用策略

Go/Rust 共享同一个 PostgreSQL 实例和相同的 schema。Kotlin 同样复用这套 schema，不另建迁移。

迁移执行依赖关系：
- `make migrate-go` 或 `make migrate-rust` 其中任意一个执行即可（schema 相同）
- Kotlin 启动时不跑迁移，仅连接已就绪的数据库

### 5.2 Exposed DSL 表定义

所有表定义在 `repository/Tables.kt`，与 Go 的 goose migration 001/002/003 一一对应：

```kotlin
// repository/Tables.kt — 全部表的 Exposed DSL 定义

object AdminUsers : LongIdTable("admin_users") {
    val username = varchar("username", 100)
    val passwordHash = varchar("password_hash", 255)
    val role = short("role").default(2)
    val fullName = varchar("full_name", 255).nullable()
    val phone = varchar("phone", 50).nullable()
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val status = short("status").default(1)
    val lastLoginAt = timestampWithTimeZone("last_login_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
}

object Customers : LongIdTable("customers") {
    val username = varchar("username", 100)
    val passwordHash = varchar("password_hash", 255)
    val name = varchar("name", 255).nullable()
    val phone = varchar("phone", 50).nullable()
    val email = varchar("email", 255).nullable()
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val signature = varchar("signature", 255).nullable()
    val isVip = bool("is_vip").default(false)
    val diamondBalance = long("diamond_balance").default(0)
    val withdrawalPasswordHash = varchar("withdrawal_password_hash", 255).nullable()
    val lastLoginAt = timestampWithTimeZone("last_login_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
}

object Videos : LongIdTable("videos") {
    val customerId = long("customer_id").references(Customers.id)
    val title = varchar("title", 500).nullable()
    val description = text("description").nullable()
    val fileName = varchar("file_name", 255)
    val fileSize = long("file_size")
    val fileUrl = varchar("file_url", 500)
    val thumbnailUrl = varchar("thumbnail_url", 500).nullable()
    val previewGifUrl = varchar("preview_gif_url", 500).nullable()
    val mimeType = varchar("mime_type", 100)
    val duration = integer("duration").nullable()
    val width = integer("width").nullable()
    val height = integer("height").nullable()
    val status = short("status").default(1)
    val reviewNotes = text("review_notes").nullable()
    val reviewedBy = long("reviewed_by").references(AdminUsers.id).nullable()
    val reviewedAt = timestampWithTimeZone("reviewed_at").nullable()
    val totalClicks = long("total_clicks").default(0)
    val validClicks = long("valid_clicks").default(0)
    val likeCount = long("like_count").default(0)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
}

// VideoPlayRecords, VideoLikes, UserFollows, TaskDefinitions, TaskProgress,
// CustomerSignInRecords — 同理，与 migration 001/003 完全对齐
```

### 5.3 HikariCP 连接池配置

```kotlin
fun createDataSource(databaseUrl: String): HikariDataSource {
    return HikariDataSource(HikariConfig().apply {
        jdbcUrl = databaseUrl.replaceFirst("postgres://", "jdbc:postgresql://")
            .replaceFirst(Regex("://([^:]+):([^@]+)@"), "://$1:$2@") // 保留用户密码
        maximumPoolSize = 20
        minimumIdle = 5
        idleTimeout = 300_000       // 5 min
        connectionTimeout = 10_000  // 10s
        maxLifetime = 1_800_000     // 30 min
        // Virtual Threads: 不需要很大的池，VT 调度器自动管理等待
    })
}
```

> **注意**：`DATABASE_URL` 格式是 `postgres://user:pass@host:port/db`，需要转换为 JDBC URL `jdbc:postgresql://host:port/db?user=...&password=...`。

## 6. 错误处理

### 6.1 设计原则

- sealed interface 穷举所有错误类型（类似 Rust 的 `AppError` enum）
- 每个错误变体携带 HTTP 状态码 + 业务错误码 + 用户消息
- Service 层抛出 `AppError`，Route 层不处理异常——全部交给 StatusPages 插件

### 6.2 AppError 定义

```kotlin
// model/Error.kt

sealed interface AppError {
    val statusCode: Int
    val errorCode: String
    val message: String

    // ── Auth ──
    data class InvalidCredentials(
        override val message: String = "invalid username or password",
    ) : AppError {
        override val statusCode = 401
        override val errorCode = "AUTH_INVALID_CREDENTIALS"
    }

    data class TokenExpired(
        override val message: String = "token expired",
    ) : AppError {
        override val statusCode = 401
        override val errorCode = "AUTH_TOKEN_EXPIRED"
    }

    data class TokenInvalid(
        override val message: String = "invalid token",
    ) : AppError {
        override val statusCode = 401
        override val errorCode = "AUTH_TOKEN_INVALID"
    }

    data class UsernameExists(
        override val message: String = "username already exists",
    ) : AppError {
        override val statusCode = 409
        override val errorCode = "AUTH_USERNAME_EXISTS"
    }

    data class PasswordMismatch(
        override val message: String = "old password is incorrect",
    ) : AppError {
        override val statusCode = 400
        override val errorCode = "AUTH_PASSWORD_MISMATCH"
    }

    // ── Resource ──
    data class NotFound(
        override val message: String,
    ) : AppError {
        override val statusCode = 404
        override val errorCode = "NOT_FOUND"
    }

    data class BadRequest(
        override val message: String,
    ) : AppError {
        override val statusCode = 400
        override val errorCode = "BAD_REQUEST"
    }

    data class Forbidden(
        override val message: String = "forbidden",
    ) : AppError {
        override val statusCode = 403
        override val errorCode = "FORBIDDEN"
    }

    // ── Video ──
    data class VideoNotFound(
        override val message: String = "video not found",
    ) : AppError {
        override val statusCode = 404
        override val errorCode = "VIDEO_NOT_FOUND"
    }

    data class AlreadyLiked(
        override val message: String = "already liked",
    ) : AppError {
        override val statusCode = 409
        override val errorCode = "VIDEO_ALREADY_LIKED"
    }

    // ── Social ──
    data class AlreadyFollowing(
        override val message: String = "already following",
    ) : AppError {
        override val statusCode = 409
        override val errorCode = "USER_ALREADY_FOLLOWING"
    }

    data class CannotFollowSelf(
        override val message: String = "cannot follow yourself",
    ) : AppError {
        override val statusCode = 400
        override val errorCode = "USER_CANNOT_FOLLOW_SELF"
    }

    data class NotFollowing(
        override val message: String = "not following this user",
    ) : AppError {
        override val statusCode = 400
        override val errorCode = "USER_NOT_FOLLOWING"
    }

    // ── Task ──
    data class TaskNotClaimable(
        override val message: String = "task is not claimable",
    ) : AppError {
        override val statusCode = 400
        override val errorCode = "TASK_NOT_CLAIMABLE"
    }

    data class AlreadySignedIn(
        override val message: String = "already signed in today",
    ) : AppError {
        override val statusCode = 409
        override val errorCode = "ALREADY_SIGNED_IN"
    }

    // ── Rate Limit ──
    data class RateLimited(
        override val message: String = "too many requests",
    ) : AppError {
        override val statusCode = 429
        override val errorCode = "RATE_LIMITED"
    }

    // ── Internal (不泄露细节) ──
    data class Internal(
        val cause: Throwable? = null,
    ) : AppError {
        override val statusCode = 500
        override val errorCode = "INTERNAL_ERROR"
        override val message = "internal server error"
    }
}
```

### 6.3 自定义异常类（用于抛出）

```kotlin
class AppException(val error: AppError) : RuntimeException(error.message)

// Service 中使用：
throw AppException(AppError.InvalidCredentials())
throw AppException(AppError.NotFound("customer not found"))
```

### 6.4 StatusPages 映射

```kotlin
// plugin/ErrorHandling.kt

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<AppException> { call, cause ->
            val err = cause.error
            call.respond(
                HttpStatusCode.fromValue(err.statusCode),
                JsonData.error(err.statusCode, err.message),
            )
        }
        exception<Throwable> { call, cause ->
            application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                JsonData.error(500, "internal server error"),
            )
        }
    }
}
```

## 7. 统一响应格式

与 Go/Rust 完全对齐：

```kotlin
// model/Response.kt

@Serializable
data class JsonData<T>(
    val code: Int,
    val message: String,
    val data: T? = null,
) {
    companion object {
        fun <T> ok(data: T): JsonData<T> = JsonData(code = 0, message = "ok", data = data)
        fun error(code: Int, message: String): JsonData<Nothing?> = JsonData(code = code, message = message, data = null)
    }
}

@Serializable
data class Paginated<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val total: Long,
)
```

Route handler 示例：

```kotlin
// 成功
call.respond(JsonData.ok(AuthResponse(accessToken, refreshToken)))

// 分页
call.respond(JsonData.ok(Paginated(items, page, pageSize, total)))
```

## 8. 认证（双域 JWT）

### 8.1 JWT 结构

与 Go 完全对齐的 claim 结构：

| 字段 | Customer | Admin |
|------|----------|-------|
| `iss` | `gabon-service` | `gabon-admin` |
| `aud` | `customer` | `admin` |
| `sub` | customer.id | admin_user.id |
| `token_type` | `access`/`refresh` | `access`/`refresh` |
| `family_id` | UUID | UUID |
| `role` | _(空)_ | `superadmin`/`admin` |
| `kid` (header) | 当前密钥 ID | 当前密钥 ID |

### 8.2 Ktor Authentication 配置

```kotlin
// plugin/Authentication.kt

fun Application.configureAuthentication(config: AppConfig, tokenStore: TokenStore) {
    install(Authentication) {
        jwt("customer") {
            verifier(
                JWT.require(Algorithm.HMAC256(config.jwt.customerSecret))
                    .withIssuer("gabon-service")
                    .withAudience("customer")
                    .build()
            )
            validate { credential ->
                val jti = credential.payload.id ?: return@validate null
                val tokenType = credential.payload.getClaim("token_type").asString()
                if (tokenType != "access") return@validate null
                if (tokenStore.isBlacklisted(jti)) return@validate null
                JWTPrincipal(credential.payload)
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, JsonData.error(401, "unauthorized"))
            }
        }

        jwt("admin") {
            verifier(
                JWT.require(Algorithm.HMAC256(config.jwt.adminSecret))
                    .withIssuer("gabon-admin")
                    .withAudience("admin")
                    .build()
            )
            validate { credential ->
                val jti = credential.payload.id ?: return@validate null
                val tokenType = credential.payload.getClaim("token_type").asString()
                if (tokenType != "access") return@validate null
                if (tokenStore.isBlacklisted(jti)) return@validate null
                JWTPrincipal(credential.payload)
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, JsonData.error(401, "unauthorized"))
            }
        }
    }
}
```

### 8.3 Token Refresh Family（Redis Lua CAS）

与 Go 实现完全对齐的 Lua 脚本：

```kotlin
// service/TokenStore.kt

class RedisTokenStore(private val redis: RedisCoroutinesCommands<String, String>) {

    suspend fun setBlacklist(jti: String, ttl: Duration) {
        redis.setex("token:blacklist:$jti", ttl.inWholeSeconds, "1")
    }

    suspend fun isBlacklisted(jti: String): Boolean {
        return redis.exists("token:blacklist:$jti") > 0
    }

    suspend fun setFamily(familyId: String, userId: Long, currentJti: String, ttl: Duration) {
        val data = """{"current_jti":"$currentJti","customer_id":$userId}"""
        redis.setex("token:family:$familyId", ttl.inWholeSeconds, data)
    }

    /**
     * 原子 CAS：比较并交换 family 的 current_jti。
     * 返回：0 = 成功，-1 = family 不存在，-2 = 重放攻击（family 已删除）
     */
    suspend fun casFamily(familyId: String, expectedJti: String, newJti: String): Long {
        val script = """
            local current = redis.call("GET", KEYS[1])
            if not current then return -1 end
            local data = cjson.decode(current)
            if data.current_jti ~= ARGV[1] then
                redis.call("DEL", KEYS[1])
                return -2
            end
            data.current_jti = ARGV[2]
            redis.call("SET", KEYS[1], cjson.encode(data), "KEEPTTL")
            return 0
        """.trimIndent()
        return redis.eval(script, ScriptOutputType.INTEGER, arrayOf("token:family:$familyId"), expectedJti, newJti)
    }

    suspend fun deleteFamily(familyId: String) {
        redis.del("token:family:$familyId")
    }
}
```

### 8.4 Route 中提取身份

```kotlin
// route/ 中的辅助扩展

fun ApplicationCall.customerId(): Long {
    val principal = principal<JWTPrincipal>("customer")
        ?: throw AppException(AppError.TokenInvalid())
    return principal.payload.subject.toLong()
}

fun ApplicationCall.adminId(): Long {
    val principal = principal<JWTPrincipal>("admin")
        ?: throw AppException(AppError.TokenInvalid())
    return principal.payload.subject.toLong()
}

fun ApplicationCall.adminRole(): String {
    val principal = principal<JWTPrincipal>("admin")
        ?: throw AppException(AppError.TokenInvalid())
    return principal.payload.getClaim("role").asString()
}
```

## 9. 中间件

### 9.1 速率限制（Redis 滑动窗口）

与 Go 实现相同的算法：Redis ZSET 滑动窗口。

```kotlin
// plugin/RateLimit.kt

data class RateLimitConfig(
    val group: String,      // "auth" | "pub" | "user" | "admin"
    val limit: Int,         // 窗口内最大请求数
    val window: Duration,   // 窗口大小
    val keyFunc: (ApplicationCall) -> String?, // 提取限流 key（IP 或 userID）
)

/**
 * 安装为 Ktor 路由级插件（createRouteScopedPlugin）。
 * 使用 Redis ZSET 滑动窗口，与 Go/Rust 实现一致。
 */
val RateLimitPlugin = createRouteScopedPlugin("RateLimit", ::RateLimitConfig) {
    val cfg = pluginConfig
    onCall { call ->
        val key = cfg.keyFunc(call) ?: return@onCall
        val redisKey = "rl:${cfg.group}:$key"
        val now = Clock.System.now()
        val windowStart = (now - cfg.window).toEpochMilliseconds().toDouble()
        val nowMicro = now.toEpochMilliseconds().toDouble() * 1000

        // Pipeline: ZREMRANGEBYSCORE + ZADD + ZCARD + EXPIRE
        val count = slidingWindowCount(redis, redisKey, windowStart, nowMicro, cfg.window)

        val remaining = maxOf(0, cfg.limit - count.toInt())
        call.response.header("X-RateLimit-Limit", cfg.limit.toString())
        call.response.header("X-RateLimit-Remaining", remaining.toString())

        if (count > cfg.limit) {
            call.response.header("Retry-After", cfg.window.inWholeSeconds.toString())
            throw AppException(AppError.RateLimited())
        }
    }
}
```

### 9.2 完整中间件栈

```kotlin
// plugin/Monitoring.kt

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        callIdMdc("call-id")
    }
    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
    }
    install(Compression) {
        gzip { priority = 1.0 }
        deflate { priority = 0.5 }
    }
    // 全局请求超时：30s（与 Go/Rust 一致）
    install(createApplicationPlugin("RequestTimeout") {
        onCall { call ->
            withTimeout(30_000) {
                proceed()
            }
        }
    })
}
```

### 9.3 中间件执行顺序

```
请求进入
  → CallId（生成/透传 X-Request-Id）
  → CallLogging（记录请求日志）
  → CORS
  → Compression
  → RequestTimeout（30s）
  → StatusPages（异常捕获）
  → Authentication（JWT 验证，按路由组选择 customer/admin）
  → RateLimit（按路由组应用不同限流策略）
  → Route Handler
```

## 10. S3 集成

### 10.1 StorageService

```kotlin
// service/StorageService.kt

class StorageService(private val config: S3Config) {

    private val client: S3Client? = if (config.isConfigured) {
        S3Client {
            region = config.region
            endpointUrl = Url.parse(config.endpoint)
            credentialsProvider = StaticCredentialsProvider(
                Credentials(config.accessKey, config.secretKey)
            )
            forcePathStyle = true // Garage 使用 path-style
        }
    } else null

    /** 生成预签名上传 URL（PUT）。客户端直传 S3。 */
    suspend fun presignUpload(bucket: String, key: String, contentType: String, expireMinutes: Int = 15): String {
        if (client == null) return "https://stub.local/presign-put/$bucket/$key"
        val request = PutObjectRequest {
            this.bucket = bucket
            this.key = key
            this.contentType = contentType
        }
        val presigned = client.presignPutObject(request, expireMinutes.minutes)
        return presigned.url.toString()
    }

    /** 构建对象的公共访问 URL。 */
    fun buildPublicUrl(bucket: String, key: String): String {
        if (client == null) return "https://stub.local/storage/$bucket/$key"
        return "${config.endpoint.trimEnd('/')}/$bucket/$key"
    }

    /** 删除对象。 */
    suspend fun delete(bucket: String, key: String) {
        client?.deleteObject {
            this.bucket = bucket
            this.key = key
        }
    }
}
```

### 10.2 上传流程

与 Go/Rust 一致的两步上传：

1. **请求预签名 URL**：`POST /api/v1/videos/upload-url` → 返回 `{ uploadUrl, key }`
2. **客户端直传 S3**：客户端用 PUT 上传到预签名 URL
3. **确认上传**：`POST /api/v1/videos/confirm-upload` → 传入 key + 元数据 → 创建 video 记录

头像上传同理：`/api/v1/users/me/avatar/upload-url` + `/api/v1/users/me/avatar/confirm`

## 11. Virtual Threads 集成

### 11.1 设计思路

Ktor + Netty 的 IO 层天然是 coroutine 驱动的（非阻塞）。但 JDBC（HikariCP + PostgreSQL）和 bcrypt 是阻塞调用。使用 Virtual Threads 作为自定义 Dispatcher，让阻塞调用不占 carrier thread。

### 11.2 自定义 Dispatcher

```kotlin
// Application.kt

/**
 * 基于 JDK 21 Virtual Threads 的 CoroutineDispatcher。
 * 所有 Exposed 事务和 bcrypt 等阻塞操作通过 withContext(Dispatchers.Loom) 执行。
 */
val Dispatchers.Loom: CoroutineDispatcher
    get() = loomDispatcher

private val loomDispatcher = Executors.newVirtualThreadPerTaskExecutor()
    .asCoroutineDispatcher()
```

### 11.3 使用模式

```kotlin
// Service 层中使用

class AuthService(
    private val customerRepo: CustomerRepo,
    // ...
) {
    suspend fun register(username: String, password: String): AuthResponse {
        // bcrypt 是 CPU-bound 阻塞操作，切到 VT
        val hash = withContext(Dispatchers.Loom) {
            BCrypt.withDefaults().hashToString(12, password.toCharArray())
        }

        // Exposed 事务也是阻塞的（JDBC），切到 VT
        val customer = withContext(Dispatchers.Loom) {
            transaction {
                customerRepo.create(username, hash)
            }
        }

        // JWT 签发是纯计算，不需要切
        val pair = jwtService.generateCustomerTokens(customer.id)
        // Redis 操作通过 Lettuce coroutine API，天然非阻塞
        tokenStore.setFamily(pair.familyId, customer.id, pair.refreshJti, config.jwt.customerRefreshTtl)

        return AuthResponse(pair.accessToken, pair.refreshToken)
    }
}
```

### 11.4 封装事务辅助函数

```kotlin
/**
 * 在 Virtual Thread 上执行 Exposed 事务。
 * 所有 Repository 调用都通过此函数。
 */
suspend fun <T> dbQuery(block: Transaction.() -> T): T =
    withContext(Dispatchers.Loom) {
        transaction { block() }
    }
```

### 11.5 Profile 2（可选：GraalVM Native Image）

仅作为未来评测选项，不在初始实现范围内：

- 替换 Netty 为 CIO 引擎（纯 Kotlin 实现，无 Netty JNI）
- 使用 GraalVM native-image 编译
- 移除 Virtual Threads（native-image 下不可用）
- 预期：启动时间 < 50ms，内存 < 30MB，但吞吐量可能低于 Netty

## 12. Application 入口

```kotlin
// Application.kt

fun main() {
    val config = AppConfig.fromEnv()

    // Database
    val dataSource = createDataSource(config.databaseUrl)
    Database.connect(dataSource)

    // Redis (Lettuce coroutine)
    val redisClient = RedisClient.create(config.redisUrl)
    val redisConn = redisClient.connect()
    val redisCommands = redisConn.coroutines()

    // Dependencies
    val tokenStore = RedisTokenStore(redisCommands)
    val jwtService = JwtService(config.jwt)
    val storageService = StorageService(config.s3)
    val customerRepo = CustomerRepo()
    val adminUserRepo = AdminUserRepo()
    val videoRepo = VideoRepo()
    val playRecordRepo = PlayRecordRepo()
    val socialRepo = SocialRepo()
    val taskRepo = TaskRepo()
    val signInRepo = SignInRepo()
    val reportRepo = ReportRepo()

    val authService = AuthService(customerRepo, tokenStore, jwtService, config.jwt)
    val adminService = AdminService(adminUserRepo, tokenStore, jwtService, config.jwt)
    val userService = UserService(customerRepo, storageService, config.s3)
    val videoService = VideoService(videoRepo, playRecordRepo, storageService, config.s3)
    val socialService = SocialService(socialRepo)
    val taskService = TaskService(taskRepo, signInRepo)
    val reportService = ReportService(reportRepo)

    embeddedServer(Netty, port = config.port) {
        configureMonitoring()
        configureErrorHandling()
        configureSerialization()
        configureAuthentication(config, tokenStore)
        configureRouting(
            authService, adminService, userService,
            videoService, socialService, taskService,
            reportService, redisCommands, config,
        )
    }.start(wait = true)

    // Shutdown hooks
    Runtime.getRuntime().addShutdownHook(Thread {
        redisConn.close()
        redisClient.shutdown()
        dataSource.close()
    })
}
```

## 13. 路由注册

```kotlin
// plugin/Routing.kt

fun Application.configureRouting(
    authService: AuthService,
    adminService: AdminService,
    userService: UserService,
    videoService: VideoService,
    socialService: SocialService,
    taskService: TaskService,
    reportService: ReportService,
    redis: RedisCoroutinesCommands<String, String>,
    config: AppConfig,
) {
    install(Routing) {
        get("/health") {
            call.respond(JsonData.ok("ok"))
        }

        // ── Customer API ──
        route("/api/v1") {
            authRoutes(authService)                      // /auth/*
            authenticate("customer") {
                userRoutes(userService)                   // /users/*
                videoRoutes(videoService, taskService)    // /videos/*
                socialRoutes(socialService)               // /customer/*
                taskRoutes(taskService)                   // /tasks/*, /activity/*
            }
        }

        // ── Admin API ──
        route("/admin/v1") {
            adminRoutes(adminService)                    // /auth/* (login 不需认证)
            authenticate("admin") {
                adminProtectedRoutes(adminService, videoService, userService, reportService)
            }
        }
    }
}
```

## 14. API 端点清单

### Customer API (`/api/v1/`)

| 方法 | 路径 | 认证 | 限流组 | 说明 |
|------|------|------|-------|------|
| POST | `/auth/register` | - | auth | 注册 |
| POST | `/auth/login` | - | auth | 登录 |
| POST | `/auth/refresh` | - | auth | 刷新 token |
| POST | `/auth/logout` | customer | user | 登出 |
| GET | `/auth/me` | customer | user | 当前用户信息 |
| PUT | `/auth/password` | customer | user | 修改密码 |
| GET | `/users/me/profile` | customer | user | 我的资料 |
| PUT | `/users/me/profile` | customer | user | 更新资料 |
| POST | `/users/me/avatar/upload-url` | customer | user | 头像预签名 |
| POST | `/users/me/avatar/confirm` | customer | user | 头像确认 |
| GET | `/users/{id}/following` | customer | user | 用户关注列表 |
| GET | `/users/{id}/followers` | customer | user | 用户粉丝列表 |
| GET | `/users/{id}/videos` | customer | pub | 用户视频列表 |
| GET | `/customer/{userId}/profile` | customer | pub | 公开资料 |
| POST | `/customer/{userId}/follow` | customer | user | 关注 |
| DELETE | `/customer/{userId}/follow` | customer | user | 取关 |
| GET | `/videos` | customer | pub | 视频列表（分页+搜索） |
| GET | `/videos/featured` | customer | pub | 精选视频 |
| GET | `/videos/my` | customer | user | 我的视频 |
| POST | `/videos/upload-url` | customer | user | 视频预签名 |
| POST | `/videos/confirm-upload` | customer | user | 视频确认 |
| GET | `/videos/{id}` | customer | pub | 视频详情 |
| DELETE | `/videos/{id}` | customer | user | 删除视频 |
| POST | `/videos/{id}/like` | customer | user | 点赞 |
| DELETE | `/videos/{id}/like` | customer | user | 取消点赞 |
| POST | `/videos/{videoId}/play-click` | customer | user | 播放点击 |
| POST | `/videos/{videoId}/play-valid` | customer | user | 有效播放 |
| GET | `/tasks` | customer | user | 任务列表 |
| POST | `/tasks/claim/{progressId}` | customer | user | 领取奖励 |
| POST | `/activity/sign-in` | customer | user | 每日签到 |

### Admin API (`/admin/v1/`)

| 方法 | 路径 | 认证 | 限流组 | 说明 |
|------|------|------|-------|------|
| POST | `/auth/login` | - | auth | 管理员登录 |
| POST | `/auth/refresh` | - | auth | 刷新 token |
| POST | `/auth/logout` | admin | admin | 管理员登出 |
| GET | `/auth/me` | admin | admin | 当前管理员信息 |
| GET | `/videos` | admin | admin | 视频列表（按状态） |
| GET | `/videos/{id}` | admin | admin | 视频详情 |
| POST | `/videos/{id}/review` | admin | admin | 审核（通过/拒绝） |
| DELETE | `/videos/{id}` | admin | admin | 删除视频 |
| GET | `/customers` | admin | admin | 客户列表 |
| PUT | `/customers/{id}/password` | admin | admin | 重置客户密码 |
| GET | `/admin-users` | admin | admin | 管理员列表 |
| POST | `/admin-users` | admin | admin | 创建管理员 |
| GET | `/admin-users/{id}` | admin | admin | 管理员详情 |
| PUT | `/admin-users/{id}` | admin | admin | 更新管理员 |
| DELETE | `/admin-users/{id}` | admin | admin | 删除管理员 |
| PUT | `/admin-users/{id}/password` | admin | admin | 修改管理员密码 |
| GET | `/reports/revenue` | admin | admin | 收入报表 |
| GET | `/reports/video/daily` | admin | admin | 视频日报 |
| GET | `/reports/video/summary` | admin | admin | 视频汇总 |

### 速率限制配置

| 组 | 限制 | 窗口 | Key |
|----|------|------|-----|
| auth | 20/min | 1 min | IP |
| pub | 120/min | 1 min | IP |
| user | 200/min | 1 min | customer_id |
| admin | 200/min | 1 min | admin_id |

## 15. 并发安全约束

与 Go/Rust 保持一致的并发安全规则：

### 15.1 计数器原子更新

```kotlin
// 点赞 — CTE 确保 INSERT 成功才 +1
Videos.update({ Videos.id eq videoId }) {
    with(SqlExpressionBuilder) {
        it[likeCount] = likeCount + 1
    }
}

// 播放计数
Videos.update({ Videos.id eq videoId }) {
    with(SqlExpressionBuilder) {
        it[totalClicks] = totalClicks + 1
    }
}
```

### 15.2 任务领取（事务 + FOR UPDATE）

```kotlin
suspend fun claimReward(progressId: Long, customerId: Long) {
    dbQuery {
        // SELECT ... FOR UPDATE 行锁
        val progress = TaskProgress
            .select(TaskProgress.id eq progressId)
            .andWhere { TaskProgress.customerId eq customerId }
            .forUpdate()
            .singleOrNull() ?: throw AppException(AppError.TaskNotClaimable())

        check(progress[TaskProgress.taskStatus] == TASK_STATUS_COMPLETED) {
            throw AppException(AppError.TaskNotClaimable("task is not completed"))
        }

        // 原子加钻石
        Customers.update({ Customers.id eq customerId }) {
            with(SqlExpressionBuilder) {
                it[diamondBalance] = diamondBalance + progress[TaskProgress.rewardDiamonds]
            }
        }

        // 标记已领取
        TaskProgress.update({ TaskProgress.id eq progressId }) {
            it[taskStatus] = TASK_STATUS_CLAIMED
            it[claimedAt] = Clock.System.now().toJavaInstant().atOffset(ZoneOffset.UTC)
        }
    }
}
```

### 15.3 Refresh Token（Redis Lua CAS）

与第 8.3 节的 `TokenStore.casFamily()` 完全一致：先签发新 token，再 CAS 交换，CAS 失败则丢弃预生成的 token。

## 16. Dockerfile

```dockerfile
# syntax=docker/dockerfile:1

# ── Build stage ──
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src/ src/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew buildFatJar --no-daemon

# ── Runtime stage ──
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache tzdata \
    && addgroup -S app && adduser -S app -G app
COPY --from=builder /build/build/libs/gabon-api.jar /app/gabon-api.jar

USER app
ENV JAVA_OPTS="--enable-preview -XX:+UseZGC -XX:+ZGenerational -Xmx256m -Xms128m"
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8090/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/gabon-api.jar"]
```

### Docker 镜像大小预估

| 服务 | 基础镜像 | 预估大小 |
|------|---------|---------|
| Go | scratch | ~12 MB |
| Rust | debian:bookworm-slim | ~25 MB |
| Java | — (外部 JVM) | — |
| **Kotlin** | eclipse-temurin:21-jre-alpine | **~180 MB** (JRE 145MB + app 35MB) |

> 如果需要更小的镜像，Profile 2 (GraalVM native-image) 可将总大小压到 ~30 MB。

## 17. Makefile

```makefile
.PHONY: dev build test lint clean docker-build docker-run

dev:
	cd kotlin && ./gradlew run --no-daemon

build:
	cd kotlin && ./gradlew buildFatJar --no-daemon

test:
	cd kotlin && ./gradlew test --no-daemon

lint:
	cd kotlin && ./gradlew detekt --no-daemon

clean:
	cd kotlin && ./gradlew clean --no-daemon

docker-build:
	docker build -t gabon-kotlin kotlin/

docker-run:
	docker run --rm --env-file .env -p 8090:8090 gabon-kotlin
```

根 Makefile 新增条目：

```makefile
# ─── Kotlin ───────────────────────────────────
dev-kotlin:
	cd kotlin && ./gradlew run --no-daemon

build-kotlin:
	cd kotlin && ./gradlew buildFatJar --no-daemon

test-kotlin:
	cd kotlin && ./gradlew test --no-daemon

lint-kotlin:
	cd kotlin && ./gradlew detekt --no-daemon

# Benchmarks
bench-k6-kotlin:
	k6 run bench/k6-scenario.js --env BASE_URL=http://localhost:8090 --env PREFIX=/api/v1
```

## 18. 测试策略

| 层 | 方法 | 工具 |
|----|------|------|
| Repository | Exposed + 真实 PG（testcontainers 或 Docker PG） | JUnit 5 |
| Service | MockK mock repo | JUnit 5 + MockK |
| Route | Ktor `testApplication` + mock service | ktor-server-test-host |
| 集成 | 完整启动 + 真实 DB + Redis | k6 / httpie |

### 必须覆盖的并发测试

与 Go 的 CI 门禁对齐：
1. 并发 refresh — 同一旧 token 并发请求仅一个成功
2. logout 后 refresh 失效
3. 并发点赞 — like_count 只增 1
4. 并发任务领取 — 仅一次成功加钻石

## 19. 预期评测指标

基于技术选型的合理预期：

| 指标 | Java | Go | Rust | Kotlin (预期) |
|------|------|-----|------|-------------|
| QPS (health) | 88K | 96K | 182K | **70-90K** |
| 内存 (RSS idle) | 354 MB | 43 MB | 19 MB | **200-280 MB** |
| 冷启动 | 2,714 ms | 33 ms | 135 ms | **1,500-2,500 ms** |
| 编译时间 | 5.6s | 10.3s | 97.8s | **8-15s** |
| 代码量 (LOC) | 7,001 | 7,087 | 4,179 | **~3,500-4,500** |

Kotlin 的预期优势：
- **代码量最少**：data class、扩展函数、DSL（Exposed、Ktor routing）极大减少样板代码
- **编译速度可接受**：K2 编译器 + Gradle daemon 增量编译在 Java/Go 之间
- **JVM 生态**：直接使用成熟的 JDBC 驱动、Lettuce 等

Kotlin 的预期劣势：
- **内存**：JVM 固有开销，但 ZGC + 256m Xmx 控制上限
- **冷启动**：JVM 启动 + class loading，但比 Spring Boot 快（Ktor 无反射扫描）
- **QPS**：受 JVM GC 暂停影响，但 Virtual Threads 减少上下文切换开销
