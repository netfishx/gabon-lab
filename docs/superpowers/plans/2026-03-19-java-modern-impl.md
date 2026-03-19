# Modern Java (Spring Boot 4.0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a modern Java backend (Spring Boot 4.0 + JDK 25) for gabon-lab, fully aligned with the Go implementation's API behavior, replacing the old Java benchmark data.

**Architecture:** Three-layer Spring Boot app (Controller → Service → Repository) using Spring Data JDBC for explicit SQL, manual JWT (no Spring Security), Virtual Threads, and modern Java patterns (Record, Sealed Interface, Pattern Matching). Shares PostgreSQL, Redis, and Garage S3 with Go/Rust/Kotlin implementations.

**Tech Stack:** Spring Boot 4.0.3, JDK 25, Spring Data JDBC, Flyway, Lettuce (Redis), com.auth0:java-jwt, AWS SDK v2 (S3), Gradle Kotlin DSL, Spotless, JUnit 5 + Testcontainers

**Spec:** `docs/superpowers/specs/2026-03-19-java-modern-design.md`

**Reference implementations:**
- Go: `go/` (primary reference for API behavior and error codes)
- Kotlin: `kotlin/` (reference for JVM patterns, build config, Flyway)

---

## File Map

All files under `java/` unless noted. `~` = project root.

### Build & Config
| File | Purpose |
|------|---------|
| `build.gradle.kts` | Gradle build with Spring Boot 4.0 plugin, Spotless, all dependencies |
| `settings.gradle.kts` | Project name |
| `src/main/resources/application.yml` | Spring Boot config (DB, Redis, JWT, S3, Virtual Threads) |
| `Dockerfile` | Multi-stage JDK 25 build |

### Common Layer
| File | Purpose |
|------|---------|
| `src/.../GabonApplication.java` | `@SpringBootApplication` entry point |
| `src/.../common/ApiResponse.java` | Unified response Record `{code, message, data}` |
| `src/.../common/AppError.java` | Sealed Interface — 19 error codes matching Go |
| `src/.../common/AppException.java` | RuntimeException wrapper for AppError |
| `src/.../common/GlobalExceptionHandler.java` | `@RestControllerAdvice` maps exceptions to ApiResponse |
| `src/.../common/PageResponse.java` | Paginated response Record `{items, total, page, pageSize}` |

### Config
| File | Purpose |
|------|---------|
| `src/.../config/AppConfig.java` | `@ConfigurationProperties` for JWT, S3 |
| `src/.../config/WebConfig.java` | Filter registration (JWT, rate limit), CORS |
| `src/.../config/RedisConfig.java` | `StringRedisTemplate` bean + Lua script beans |

### Security
| File | Purpose |
|------|---------|
| `src/.../security/JwtDomain.java` | Record: issuer, audience, secret, TTLs per domain |
| `src/.../security/JwtAuthFilter.java` | `OncePerRequestFilter` — dual-domain JWT validation |
| `src/.../security/RateLimitFilter.java` | `OncePerRequestFilter` — Redis sliding window |

### Model: Entities (Spring Data JDBC mapped)
| File | Table |
|------|-------|
| `src/.../model/entity/AdminUser.java` | `admin_users` |
| `src/.../model/entity/Customer.java` | `customers` |
| `src/.../model/entity/Video.java` | `videos` |
| `src/.../model/entity/VideoPlayRecord.java` | `video_play_records` |
| `src/.../model/entity/VideoLike.java` | `video_likes` |
| `src/.../model/entity/UserFollow.java` | `user_follows` |
| `src/.../model/entity/TaskDefinition.java` | `task_definitions` |
| `src/.../model/entity/TaskProgress.java` | `task_progress` |
| `src/.../model/entity/CustomerSignInRecord.java` | `customer_sign_in_records` |

### Model: Request/Response Records
| File | Contents |
|------|----------|
| `src/.../model/request/AuthRequests.java` | RegisterRequest, LoginRequest, RefreshRequest, UpdatePasswordRequest |
| `src/.../model/request/VideoRequests.java` | UploadUrlRequest, ConfirmUploadRequest, ReviewVideoRequest |
| `src/.../model/request/UserRequests.java` | UpdateProfileRequest |
| `src/.../model/request/AdminRequests.java` | CreateAdminRequest, UpdateAdminRequest, ResetPasswordRequest |
| `src/.../model/response/AuthResponses.java` | TokenPairResponse, CustomerMeResponse |
| `src/.../model/response/VideoResponses.java` | VideoDetailResponse, VideoListItemResponse |
| `src/.../model/response/UserResponses.java` | UserProfileResponse, FollowUserResponse |
| `src/.../model/response/AdminResponses.java` | AdminUserResponse, CustomerListResponse, ReportResponse |
| `src/.../model/response/TaskResponses.java` | TaskItemResponse, SignInResponse |

### Repositories (Spring Data JDBC + @Query)
| File | Primary table |
|------|--------------|
| `src/.../repository/CustomerRepository.java` | customers |
| `src/.../repository/VideoRepository.java` | videos |
| `src/.../repository/VideoLikeRepository.java` | video_likes |
| `src/.../repository/VideoPlayRecordRepository.java` | video_play_records |
| `src/.../repository/UserFollowRepository.java` | user_follows |
| `src/.../repository/AdminUserRepository.java` | admin_users |
| `src/.../repository/TaskDefinitionRepository.java` | task_definitions |
| `src/.../repository/TaskProgressRepository.java` | task_progress |
| `src/.../repository/CustomerSignInRecordRepository.java` | sign_in_records |

### Services
| File | Responsibility |
|------|---------------|
| `src/.../service/JwtService.java` | JWT sign/verify for dual domains |
| `src/.../service/TokenStore.java` | Redis: refresh token family CAS, access token blacklist |
| `src/.../service/AuthService.java` | Register, login, refresh, logout, me, password |
| `src/.../service/VideoService.java` | Video CRUD, like/unlike (CTE), play tracking |
| `src/.../service/UserService.java` | Profile, avatar, follow/unfollow |
| `src/.../service/TaskService.java` | Task list, claim (FOR UPDATE), period key generation |
| `src/.../service/ActivityService.java` | Daily sign-in |
| `src/.../service/StorageService.java` | S3 presigned URL generation |
| `src/.../service/AdminService.java` | Admin auth, admin user CRUD, customer mgmt, video review, reports |

### Controllers
| File | Prefix | Endpoints |
|------|--------|-----------|
| `src/.../controller/api/AuthController.java` | `/api/v1/auth` | register, login, refresh, logout, me, password |
| `src/.../controller/api/VideoController.java` | `/api/v1/videos` | list, featured, me, upload-url, confirm, {id}, like, play |
| `src/.../controller/api/UserController.java` | `/api/v1/users` | profile, avatar, {id}, follow, following, followers, videos |
| `src/.../controller/api/TaskController.java` | `/api/v1/tasks` | list, claim |
| `src/.../controller/api/ActivityController.java` | `/api/v1/activity` | sign-in |
| `src/.../controller/admin/AdminAuthController.java` | `/admin/v1/auth` | login, refresh, logout, me |
| `src/.../controller/admin/AdminUserController.java` | `/admin/v1/admin-users` | CRUD + password reset |
| `src/.../controller/admin/CustomerController.java` | `/admin/v1/customers` | list, password reset |
| `src/.../controller/admin/VideoReviewController.java` | `/admin/v1/videos` | list, detail, review, delete |
| `src/.../controller/admin/ReportController.java` | `/admin/v1/reports` | revenue, video/daily, video/summary |

### Database Migration
| File | Purpose |
|------|---------|
| `src/main/resources/db/migration/V001__init.sql` | Full PG schema (idempotent, reuses Go schema) |

### Infrastructure (project root `~`)
| File | Change |
|------|--------|
| `~/Makefile` | Add Java commands, update aggregates |
| `~/.env.example` | Add `JAVA_PORT=8082` |
| `~/bench/correctness.sh` | Add "Go vs Java" section |
| `~/bench/metrics.sh` | Add "[Java]" to each metric |
| `~/bench/oha-endpoints.sh` | Add Java service detection + benchmarking |

### Tests
| File | Scope |
|------|-------|
| `src/test/.../service/AuthServiceTest.java` | Unit: auth business logic |
| `src/test/.../service/VideoServiceTest.java` | Unit: video business logic |
| `src/test/.../controller/AuthControllerIT.java` | Integration: full auth flow with Testcontainers |
| `src/test/.../controller/VideoControllerIT.java` | Integration: video endpoints |
| `src/test/.../ConcurrencyIT.java` | Integration: 4 required concurrency scenarios |

---

## Task 1: Project Scaffolding

**Files:**
- Create: `java/build.gradle.kts`
- Create: `java/settings.gradle.kts`
- Create: `java/src/main/java/lab/gabon/GabonApplication.java`
- Create: `java/src/main/resources/application.yml`

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
rootProject.name = "gabon-java"
```

- [ ] **Step 2: Create `build.gradle.kts`**

Use `context7` or Spring Initializr to verify exact Spring Boot 4.0.3 plugin coordinates and dependency versions before writing. The following is the target structure:

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.0.2"
    id("org.flywaydb.flyway") version "11.8.0"
}

group = "lab.gabon"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Database
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // JWT
    implementation("com.auth0:java-jwt:4.5.0")

    // Password hashing
    implementation("org.springframework.security:spring-security-crypto")

    // S3
    implementation("software.amazon.awssdk:s3:2.31.1")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql:1.20.6")
    testImplementation("org.testcontainers:junit-jupiter:1.20.6")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

**Important:** Verify all dependency versions with `context7` or Maven Central search. The versions above are targets — use the latest stable.

- [ ] **Step 3: Create `GabonApplication.java`**

```java
package lab.gabon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableConfigurationProperties(AppConfig.class)
public class GabonApplication {

    public static void main(String[] args) {
        SpringApplication.run(GabonApplication.class, args);
    }
}
```

- [ ] **Step 4: Create `application.yml`**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:gabon_lab}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
  data:
    redis:
      url: ${REDIS_URL:redis://:benchpass@localhost:6379/0}
  threads:
    virtual:
      enabled: true
  jackson:
    property-naming-strategy: LOWER_CAMEL_CASE
    default-property-inclusion: non_null

server:
  port: ${JAVA_PORT:8082}

# Custom config (read by AppConfig)
app:
  jwt:
    customer-secret: ${JWT_CUSTOMER_SECRET:change-me-customer-secret-min-32-chars}
    customer-access-ttl: ${JWT_CUSTOMER_ACCESS_TTL:15m}
    customer-refresh-ttl: ${JWT_CUSTOMER_REFRESH_TTL:168h}
    admin-secret: ${JWT_ADMIN_SECRET:change-me-admin-secret-min-32-chars}
    admin-access-ttl: ${JWT_ADMIN_ACCESS_TTL:15m}
    admin-refresh-ttl: ${JWT_ADMIN_REFRESH_TTL:168h}
    current-kid: ${JWT_CURRENT_KID:key-2026-03}
  s3:
    endpoint: ${S3_ENDPOINT:http://localhost:3900}
    region: ${S3_REGION:garage}
    access-key: ${S3_ACCESS_KEY:}
    secret-key: ${S3_SECRET_KEY:}
    bucket-videos: ${S3_BUCKET_VIDEOS:gabon-videos}
    bucket-avatars: ${S3_BUCKET_AVATARS:gabon-avatars}
```

- [ ] **Step 5: Initialize Gradle wrapper**

Copy wrapper from the Kotlin project (known working), then update version if needed:

```bash
cp -r ../kotlin/gradle java/gradle
cp ../kotlin/gradlew ../kotlin/gradlew.bat java/
```

Or if `gradle` CLI is available locally: `cd java && gradle wrapper --gradle-version 8.13`

- [ ] **Step 6: Verify project compiles**

```bash
cd java && ./gradlew build -x test --no-daemon
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add java/
git commit -m "feat(java): scaffold Spring Boot 4.0 project

Add build.gradle.kts with Spring Boot 4.0.3, JDK 25 toolchain,
Spotless, Flyway, Spring Data JDBC, Redis, JWT, S3 dependencies.
Virtual Threads enabled in application.yml."
```

---

## Task 2: Common Layer

**Files:**
- Create: `java/src/main/java/lab/gabon/common/ApiResponse.java`
- Create: `java/src/main/java/lab/gabon/common/PageResponse.java`
- Create: `java/src/main/java/lab/gabon/common/AppError.java`
- Create: `java/src/main/java/lab/gabon/common/AppException.java`
- Create: `java/src/main/java/lab/gabon/common/GlobalExceptionHandler.java`

- [ ] **Step 1: Create `ApiResponse.java`**

Response format matches Go/Rust/Kotlin: `int code` (success=0, error=HTTP status).
Success: `{ "code": 0, "message": "ok", "data": {...} }`
Error: `{ "code": 401, "message": "invalid username or password", "data": null }`

```java
package lab.gabon.common;

public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static ApiResponse<Void> error(AppError error) {
        return new ApiResponse<>(error.status(), error.message(), null);
    }
}
```

- [ ] **Step 2: Create `PageResponse.java`**

```java
package lab.gabon.common;

import java.util.List;

public record PageResponse<T>(List<T> items, long total, int page, int pageSize) {}
```

- [ ] **Step 3: Create `AppError.java`**

All 19 error codes from Go `model/errors.go`. Sealed Interface with Record implementations.

`errorCode()` is for internal logging/debugging only. The response envelope uses `status()` as `code` and `message()` as `message` — matching Go/Rust/Kotlin `{ "code": <http_status>, "message": "..." }`.

```java
package lab.gabon.common;

public sealed interface AppError {

    String errorCode();  // Internal identifier, e.g. "AUTH_INVALID_CREDENTIALS" (for logs)
    String message();    // Human-readable, maps to response.message
    int status();        // HTTP status code, maps to response.code

    // Generic errors
    record Internal(String message) implements AppError {
        public String errorCode() { return "INTERNAL_ERROR"; }
        public int status() { return 500; }
    }
    record BadRequest(String message) implements AppError {
        public String errorCode() { return "BAD_REQUEST"; }
        public int status() { return 400; }
    }
    record NotFound(String message) implements AppError {
        public String errorCode() { return "NOT_FOUND"; }
        public int status() { return 404; }
    }
    record Unauthorized() implements AppError {
        public String errorCode() { return "UNAUTHORIZED"; }
        public String message() { return "unauthorized"; }
        public int status() { return 401; }
    }
    record Forbidden() implements AppError {
        public String errorCode() { return "FORBIDDEN"; }
        public String message() { return "forbidden"; }
        public int status() { return 403; }
    }

    // Auth errors
    record InvalidCredentials() implements AppError {
        public String errorCode() { return "AUTH_INVALID_CREDENTIALS"; }
        public String message() { return "invalid username or password"; }
        public int status() { return 401; }
    }
    record TokenExpired() implements AppError {
        public String errorCode() { return "AUTH_TOKEN_EXPIRED"; }
        public String message() { return "token expired"; }
        public int status() { return 401; }
    }
    record TokenInvalid() implements AppError {
        public String errorCode() { return "AUTH_TOKEN_INVALID"; }
        public String message() { return "token invalid"; }
        public int status() { return 401; }
    }
    record UsernameExists() implements AppError {
        public String errorCode() { return "AUTH_USERNAME_EXISTS"; }
        public String message() { return "username already exists"; }
        public int status() { return 409; }
    }
    record PasswordMismatch() implements AppError {
        public String errorCode() { return "AUTH_PASSWORD_MISMATCH"; }
        public String message() { return "current password is incorrect"; }
        public int status() { return 400; }
    }

    // Video errors
    record VideoNotFound() implements AppError {
        public String errorCode() { return "VIDEO_NOT_FOUND"; }
        public String message() { return "video not found"; }
        public int status() { return 404; }
    }
    record VideoNotApproved() implements AppError {
        public String errorCode() { return "VIDEO_NOT_APPROVED"; }
        public String message() { return "video not approved"; }
        public int status() { return 403; }
    }
    record AlreadyLiked() implements AppError {
        public String errorCode() { return "VIDEO_ALREADY_LIKED"; }
        public String message() { return "already liked"; }
        public int status() { return 409; }
    }

    // User errors
    record AlreadyFollowing() implements AppError {
        public String errorCode() { return "USER_ALREADY_FOLLOWING"; }
        public String message() { return "already following"; }
        public int status() { return 409; }
    }
    record CannotFollowSelf() implements AppError {
        public String errorCode() { return "USER_CANNOT_FOLLOW_SELF"; }
        public String message() { return "cannot follow yourself"; }
        public int status() { return 400; }
    }
    record NotFollowing() implements AppError {
        public String errorCode() { return "USER_NOT_FOLLOWING"; }
        public String message() { return "not following"; }
        public int status() { return 400; }
    }

    // Task errors
    record TaskNotClaimable() implements AppError {
        public String errorCode() { return "TASK_NOT_CLAIMABLE"; }
        public String message() { return "task not claimable"; }
        public int status() { return 400; }
    }
    record AlreadySignedIn() implements AppError {
        public String errorCode() { return "ALREADY_SIGNED_IN"; }
        public String message() { return "already signed in today"; }
        public int status() { return 409; }
    }

    // Rate limit
    record RateLimited() implements AppError {
        public String errorCode() { return "RATE_LIMITED"; }
        public String message() { return "too many requests"; }
        public int status() { return 429; }
    }
}
```

- [ ] **Step 4: Create `AppException.java`**

```java
package lab.gabon.common;

public class AppException extends RuntimeException {

    private final AppError error;

    public AppException(AppError error) {
        super(error.errorCode() + ": " + error.message());
        this.error = error;
    }

    public AppError getError() {
        return error;
    }
}
```

- [ ] **Step 5: Create `GlobalExceptionHandler.java`**

```java
package lab.gabon.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleApp(AppException ex) {
        var error = ex.getError();
        return ResponseEntity.status(error.status()).body(ApiResponse.error(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("validation failed");
        var error = new AppError.BadRequest(message);
        return ResponseEntity.badRequest().body(ApiResponse.error(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        var error = new AppError.Internal("internal server error");
        return ResponseEntity.internalServerError().body(ApiResponse.error(error));
    }
}
```

- [ ] **Step 6: Verify compile**

```bash
cd java && ./gradlew build -x test --no-daemon
```

- [ ] **Step 7: Commit**

```bash
git add java/src/main/java/lab/gabon/common/
git commit -m "feat(java): add common layer

ApiResponse, PageResponse, AppError (sealed interface with 19 error
codes matching Go), AppException, GlobalExceptionHandler."
```

---

## Task 3: Database Entities + Flyway Migration

**Files:**
- Create: `java/src/main/resources/db/migration/V001__init.sql`
- Create: 9 entity files under `java/src/main/java/lab/gabon/model/entity/`

- [ ] **Step 1: Create `V001__init.sql`**

Copy from `go/db/migrations/001_init.sql` (minus goose directives), and add the `sign_in_records` table. Use `IF NOT EXISTS` for idempotency since other implementations share the same PG database.

**Note:** The `customer_sign_in_records` table matches Go migration `003_add_sign_in.sql` exactly. All implementations share this table in the same PG database.

```sql
-- Reuses schema from Go/Kotlin implementations.
-- IF NOT EXISTS ensures idempotency when sharing the same PG database.

CREATE TABLE IF NOT EXISTS admin_users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            SMALLINT NOT NULL DEFAULT 2,
    full_name       VARCHAR(255),
    phone           VARCHAR(50),
    avatar_url      VARCHAR(500),
    status          SMALLINT NOT NULL DEFAULT 1,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_admin_users_username_active
    ON admin_users(LOWER(username)) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS customers (
    id                          BIGSERIAL PRIMARY KEY,
    username                    VARCHAR(100) NOT NULL,
    password_hash               VARCHAR(255) NOT NULL,
    name                        VARCHAR(255),
    phone                       VARCHAR(50),
    email                       VARCHAR(255),
    avatar_url                  VARCHAR(500),
    signature                   VARCHAR(255),
    is_vip                      BOOLEAN NOT NULL DEFAULT FALSE,
    diamond_balance             BIGINT NOT NULL DEFAULT 0,
    withdrawal_password_hash    VARCHAR(255),
    last_login_at               TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_username_active
    ON customers(LOWER(username)) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS videos (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    title           VARCHAR(500),
    description     TEXT,
    file_name       VARCHAR(255) NOT NULL,
    file_size       BIGINT NOT NULL,
    file_url        VARCHAR(500) NOT NULL,
    thumbnail_url   VARCHAR(500),
    preview_gif_url VARCHAR(500),
    mime_type       VARCHAR(100) NOT NULL,
    duration        INT,
    width           INT,
    height          INT,
    status          SMALLINT NOT NULL DEFAULT 1,
    review_notes    TEXT,
    reviewed_by     BIGINT REFERENCES admin_users(id),
    reviewed_at     TIMESTAMPTZ,
    total_clicks    BIGINT NOT NULL DEFAULT 0,
    valid_clicks    BIGINT NOT NULL DEFAULT 0,
    like_count      BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_videos_customer_id ON videos(customer_id);
CREATE INDEX IF NOT EXISTS idx_videos_status ON videos(status) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS video_play_records (
    id              BIGSERIAL PRIMARY KEY,
    video_id        BIGINT NOT NULL REFERENCES videos(id),
    customer_id     BIGINT REFERENCES customers(id),
    play_type       SMALLINT NOT NULL,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_play_records_video ON video_play_records(video_id);

CREATE TABLE IF NOT EXISTS video_likes (
    id              BIGSERIAL PRIMARY KEY,
    video_id        BIGINT NOT NULL REFERENCES videos(id),
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(video_id, customer_id)
);

CREATE TABLE IF NOT EXISTS user_follows (
    id              BIGSERIAL PRIMARY KEY,
    follower_id     BIGINT NOT NULL REFERENCES customers(id),
    followed_id     BIGINT NOT NULL REFERENCES customers(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(follower_id, followed_id),
    CHECK(follower_id != followed_id)
);
CREATE INDEX IF NOT EXISTS idx_follows_followed ON user_follows(followed_id);

CREATE TABLE IF NOT EXISTS task_definitions (
    id              BIGSERIAL PRIMARY KEY,
    task_code       VARCHAR(100) UNIQUE NOT NULL,
    task_name       VARCHAR(255) NOT NULL,
    description     TEXT,
    task_type       SMALLINT NOT NULL,
    task_category   SMALLINT NOT NULL,
    target_count    INT NOT NULL,
    reward_diamonds INT NOT NULL,
    icon_url        VARCHAR(500),
    display_order   INT NOT NULL DEFAULT 0,
    vip_only        BOOLEAN NOT NULL DEFAULT FALSE,
    status          SMALLINT NOT NULL DEFAULT 1,
    start_time      TIMESTAMPTZ,
    end_time        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS task_progress (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    task_id         BIGINT NOT NULL REFERENCES task_definitions(id),
    current_count   INT NOT NULL DEFAULT 0,
    target_count    INT NOT NULL,
    period_key      VARCHAR(50) NOT NULL,
    task_status     SMALLINT NOT NULL DEFAULT 1,
    reward_diamonds INT NOT NULL,
    completed_at    TIMESTAMPTZ,
    claimed_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(customer_id, task_id, period_key)
);
CREATE INDEX IF NOT EXISTS idx_task_progress_customer ON task_progress(customer_id);

CREATE TABLE IF NOT EXISTS customer_sign_in_records (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    period_key      VARCHAR(50) NOT NULL,
    reward_diamonds INT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(customer_id, period_key)
);
CREATE INDEX IF NOT EXISTS idx_sign_in_records_customer ON customer_sign_in_records(customer_id);
```

- [ ] **Step 2: Create entity classes**

Spring Data JDBC uses `@Table` + `@Id` annotations. All entities follow this pattern:

```java
package lab.gabon.model.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;

@Table("customers")
public record Customer(
    @Id Long id,
    String username,
    String passwordHash,
    String name,
    String phone,
    String email,
    String avatarUrl,
    String signature,
    boolean isVip,
    long diamondBalance,
    String withdrawalPasswordHash,
    Instant lastLoginAt,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt
) {}
```

Create all 9 entity Records following this pattern. Each field maps 1:1 to the DB column (Spring Data JDBC uses snake_case → camelCase by default via `NamingStrategy`).

Entity files: `AdminUser.java`, `Customer.java`, `Video.java`, `VideoPlayRecord.java`, `VideoLike.java`, `UserFollow.java`, `TaskDefinition.java`, `TaskProgress.java`, `SignInRecord.java`.

Key mappings to get right:
- `Video.status`: SMALLINT → `short` (1=pending, 3=review, 4=approved, 5=rejected)
- `TaskProgress.taskStatus`: SMALLINT → `short` (1=in_progress, 2=completed, 3=claimed)
- `AdminUser.role`: SMALLINT → `short` (1=superadmin, 2=admin)
- `SignInRecord.signInDate`: DATE → `java.time.LocalDate`
- All timestamps: TIMESTAMPTZ → `java.time.Instant`

- [ ] **Step 3: Verify Flyway migration runs**

Start Docker containers and verify:

```bash
# From project root
make up
cd java && ./gradlew bootRun --no-daemon
# Should see "Successfully applied N migration(s)" in logs
# Ctrl+C to stop
```

- [ ] **Step 4: Commit**

```bash
git add java/src/main/resources/db/ java/src/main/java/lab/gabon/model/entity/
git commit -m "feat(java): add Flyway migration and entity classes

V001__init.sql reuses Go PG schema (IF NOT EXISTS for shared DB).
9 entity Records mapped with Spring Data JDBC annotations."
```

---

## Task 4: Repositories

**Files:**
- Create: 9 repository interfaces under `java/src/main/java/lab/gabon/repository/`

- [ ] **Step 1: Create all repository interfaces**

Each repository extends `CrudRepository<Entity, Long>` and adds custom `@Query` methods.

**Key SQL patterns to implement:**

1. **Soft delete queries** — always include `WHERE deleted_at IS NULL`:
```java
@Query("SELECT * FROM customers WHERE id = :id AND deleted_at IS NULL")
Optional<Customer> findActiveById(long id);
```

2. **Username lookup (LOWER)** — always use `LOWER()`:
```java
@Query("SELECT * FROM customers WHERE LOWER(username) = LOWER(:username) AND deleted_at IS NULL")
Optional<Customer> findByUsername(String username);
```

3. **Atomic counters** — never read-modify-write:
```java
@Modifying
@Query("UPDATE videos SET like_count = like_count + 1 WHERE id = :id")
void incrementLikeCount(long id);
```

4. **CTE for like (atomic insert + count increment)**:
```java
@Modifying
@Query("""
    WITH inserted AS (
        INSERT INTO video_likes (video_id, customer_id)
        VALUES (:videoId, :customerId)
        ON CONFLICT (video_id, customer_id) DO NOTHING
        RETURNING id
    )
    UPDATE videos SET like_count = like_count + 1
    WHERE id = :videoId AND EXISTS (SELECT 1 FROM inserted)
    """)
int likeVideo(long videoId, long customerId);
```

5. **CTE for unlike (atomic delete + count decrement)**:
```java
@Modifying
@Query("""
    WITH deleted AS (
        DELETE FROM video_likes
        WHERE video_id = :videoId AND customer_id = :customerId
        RETURNING id
    )
    UPDATE videos SET like_count = like_count - 1
    WHERE id = :videoId AND EXISTS (SELECT 1 FROM deleted)
    """)
int unlikeVideo(long videoId, long customerId);
```

6. **FOR UPDATE (task claim)**:
```java
@Query("SELECT * FROM task_progress WHERE id = :id FOR UPDATE")
Optional<TaskProgress> findByIdForUpdate(long id);
```

7. **Paginated queries** — use `LIMIT :limit OFFSET :offset`:
```java
@Query("""
    SELECT * FROM videos
    WHERE status = 4 AND deleted_at IS NULL
    ORDER BY created_at DESC
    LIMIT :limit OFFSET :offset
    """)
List<Video> findApprovedVideos(int limit, int offset);

@Query("SELECT COUNT(*) FROM videos WHERE status = 4 AND deleted_at IS NULL")
long countApprovedVideos();
```

Create all 9 repository interfaces with the queries needed by each service. Refer to Go `db/queries/` for the complete list of SQL operations per table.

- [ ] **Step 2: Verify compile**

```bash
cd java && ./gradlew build -x test --no-daemon
```

- [ ] **Step 3: Commit**

```bash
git add java/src/main/java/lab/gabon/repository/
git commit -m "feat(java): add repository layer with @Query SQL

9 Spring Data JDBC repositories. Key patterns: soft delete WHERE,
LOWER(username), atomic CTE like/unlike, FOR UPDATE task claim,
paginated queries with LIMIT/OFFSET."
```

---

## Task 5: JWT + Token Store

**Files:**
- Create: `java/src/main/java/lab/gabon/config/AppConfig.java`
- Create: `java/src/main/java/lab/gabon/security/JwtDomain.java`
- Create: `java/src/main/java/lab/gabon/service/JwtService.java`
- Create: `java/src/main/java/lab/gabon/service/TokenStore.java`
- Create: `java/src/main/java/lab/gabon/config/RedisConfig.java`

- [ ] **Step 1: Create `AppConfig.java`**

```java
package lab.gabon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppConfig(JwtConfig jwt, S3Config s3) {

    public record JwtConfig(
        String customerSecret,
        Duration customerAccessTtl,
        Duration customerRefreshTtl,
        String adminSecret,
        Duration adminAccessTtl,
        Duration adminRefreshTtl,
        String currentKid
    ) {}

    public record S3Config(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucketVideos,
        String bucketAvatars
    ) {}
}
```

Note: `@EnableConfigurationProperties(AppConfig.class)` is already added to `GabonApplication.java` in Task 1.

- [ ] **Step 2: Create `JwtDomain.java`**

```java
package lab.gabon.security;

import java.time.Duration;

public record JwtDomain(
    String issuer,
    String audience,
    String secret,
    Duration accessTtl,
    Duration refreshTtl
) {
    public static final String CUSTOMER_ISSUER = "gabon-service";
    public static final String CUSTOMER_AUDIENCE = "customer";
    public static final String ADMIN_ISSUER = "gabon-admin";
    public static final String ADMIN_AUDIENCE = "admin";
}
```

- [ ] **Step 3: Create `JwtService.java`**

```java
package lab.gabon.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lab.gabon.config.AppConfig;
import lab.gabon.security.JwtDomain;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtDomain customerDomain;
    private final JwtDomain adminDomain;
    private final String currentKid;

    public JwtService(AppConfig config) {
        var jwt = config.jwt();
        this.customerDomain = new JwtDomain(
                JwtDomain.CUSTOMER_ISSUER, JwtDomain.CUSTOMER_AUDIENCE,
                jwt.customerSecret(), jwt.customerAccessTtl(), jwt.customerRefreshTtl());
        this.adminDomain = new JwtDomain(
                JwtDomain.ADMIN_ISSUER, JwtDomain.ADMIN_AUDIENCE,
                jwt.adminSecret(), jwt.adminAccessTtl(), jwt.adminRefreshTtl());
        this.currentKid = jwt.currentKid();
    }

    public record TokenPair(String accessToken, String refreshToken, String familyId) {}

    public TokenPair issueCustomerTokens(long userId, String existingFamilyId) {
        return issueTokens(userId, customerDomain, existingFamilyId, null);
    }

    public TokenPair issueAdminTokens(long userId, String existingFamilyId, String role) {
        return issueTokens(userId, adminDomain, existingFamilyId, role);
    }

    public DecodedJWT verifyCustomerAccess(String token) {
        return verify(token, customerDomain, "access");
    }

    public DecodedJWT verifyAdminAccess(String token) {
        return verify(token, adminDomain, "access");
    }

    public DecodedJWT verifyCustomerRefresh(String token) {
        return verify(token, customerDomain, "refresh");
    }

    public DecodedJWT verifyAdminRefresh(String token) {
        return verify(token, adminDomain, "refresh");
    }

    private TokenPair issueTokens(long userId, JwtDomain domain, String existingFamilyId, String role) {
        String familyId = existingFamilyId != null ? existingFamilyId : UUID.randomUUID().toString();
        var now = Instant.now();

        var accessBuilder = JWT.create()
                .withIssuer(domain.issuer())
                .withAudience(domain.audience())
                .withKeyId(currentKid)
                .withSubject(String.valueOf(userId))
                .withJWTId(UUID.randomUUID().toString())
                .withClaim("token_type", "access")
                .withClaim("family_id", familyId)
                .withIssuedAt(now)
                .withExpiresAt(now.plus(domain.accessTtl()));
        if (role != null) {
            accessBuilder = accessBuilder.withClaim("role", role);
        }
        String accessToken = accessBuilder.sign(Algorithm.HMAC256(domain.secret()));

        var refreshBuilder = JWT.create()
                .withIssuer(domain.issuer())
                .withAudience(domain.audience())
                .withKeyId(currentKid)
                .withSubject(String.valueOf(userId))
                .withJWTId(UUID.randomUUID().toString())
                .withClaim("token_type", "refresh")
                .withClaim("family_id", familyId)
                .withIssuedAt(now)
                .withExpiresAt(now.plus(domain.refreshTtl()));
        if (role != null) {
            refreshBuilder = refreshBuilder.withClaim("role", role);
        }
        String refreshToken = refreshBuilder.sign(Algorithm.HMAC256(domain.secret()));

        return new TokenPair(accessToken, refreshToken, familyId);
    }

    private DecodedJWT verify(String token, JwtDomain domain, String expectedTokenType) {
        var decoded = JWT.require(Algorithm.HMAC256(domain.secret()))
                .withIssuer(domain.issuer())
                .withAudience(domain.audience())
                .build()
                .verify(token);
        String tokenType = decoded.getClaim("token_type").asString();
        if (!expectedTokenType.equals(tokenType)) {
            throw new com.auth0.jwt.exceptions.JWTVerificationException("wrong token type");
        }
        return decoded;
    }

    public JwtDomain getCustomerDomain() { return customerDomain; }
    public JwtDomain getAdminDomain() { return adminDomain; }
}
```

- [ ] **Step 4: Create `RedisConfig.java`**

```java
package lab.gabon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean("refreshCasScript")
    public RedisScript<Long> refreshCasScript() {
        String lua = """
                local v = redis.call('GET', KEYS[1])
                if v == false then return -1 end
                local sep = string.find(v, ':', 1, true)
                local uid = string.sub(v, 1, sep - 1)
                local jti = string.sub(v, sep + 1)
                if jti ~= ARGV[1] then
                  redis.call('DEL', KEYS[1])
                  return -2
                end
                redis.call('SET', KEYS[1], uid .. ':' .. ARGV[2], 'KEEPTTL')
                return tonumber(uid)
                """;
        return RedisScript.of(lua, Long.class);
    }
}
```

- [ ] **Step 5: Create `TokenStore.java`**

```java
package lab.gabon.service;

import lab.gabon.config.AppConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class TokenStore {

    private final StringRedisTemplate redis;
    private final RedisScript<Long> refreshCasScript;
    private final Duration customerRefreshTtl;
    private final Duration adminRefreshTtl;
    private final Duration customerAccessTtl;
    private final Duration adminAccessTtl;

    public TokenStore(StringRedisTemplate redis,
                      @Qualifier("refreshCasScript") RedisScript<Long> refreshCasScript,
                      AppConfig config) {
        this.redis = redis;
        this.refreshCasScript = refreshCasScript;
        this.customerRefreshTtl = config.jwt().customerRefreshTtl();
        this.adminRefreshTtl = config.jwt().adminRefreshTtl();
        this.customerAccessTtl = config.jwt().customerAccessTtl();
        this.adminAccessTtl = config.jwt().adminAccessTtl();
    }

    // Store refresh token family: token:family:{familyId} -> "{userId}:{refreshJti}"
    public void storeRefreshFamily(String familyId, long userId, String refreshJti, boolean isAdmin) {
        String key = "token:family:" + familyId;
        String value = userId + ":" + refreshJti;
        Duration ttl = isAdmin ? adminRefreshTtl : customerRefreshTtl;
        redis.opsForValue().set(key, value, ttl);
    }

    // Atomic CAS: verify old JTI, replace with new JTI. Returns userId or error code.
    // -1 = family not found (expired/revoked), -2 = JTI mismatch (replay attack, family deleted)
    public long rotateRefreshToken(String familyId, String oldJti, String newJti) {
        String key = "token:family:" + familyId;
        Long result = redis.execute(refreshCasScript, List.of(key), oldJti, newJti);
        return result != null ? result : -1;
    }

    // Revoke entire refresh family (logout)
    public void revokeFamily(String familyId) {
        redis.delete("token:family:" + familyId);
    }

    // Blacklist an access token (logout: block until expiry)
    public void blacklistAccessToken(String jti, boolean isAdmin) {
        String key = "token:blacklist:" + jti;
        Duration ttl = isAdmin ? adminAccessTtl : customerAccessTtl;
        redis.opsForValue().set(key, "1", ttl);
    }

    // Check if access token is blacklisted
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey("token:blacklist:" + jti));
    }
}
```

- [ ] **Step 6: Verify compile**

```bash
cd java && ./gradlew build -x test --no-daemon
```

- [ ] **Step 7: Commit**

```bash
git add java/src/main/java/lab/gabon/config/ java/src/main/java/lab/gabon/security/JwtDomain.java java/src/main/java/lab/gabon/service/JwtService.java java/src/main/java/lab/gabon/service/TokenStore.java
git commit -m "feat(java): add JWT dual-domain auth + Redis token store

JwtService issues/verifies tokens for customer and admin domains.
TokenStore uses Redis Lua CAS for atomic refresh token rotation
and blacklist for access token revocation on logout."
```

---

## Task 6: Auth Filters + Rate Limiting + WebConfig

**Files:**
- Create: `java/src/main/java/lab/gabon/security/JwtAuthFilter.java`
- Create: `java/src/main/java/lab/gabon/security/RateLimitFilter.java`
- Create: `java/src/main/java/lab/gabon/config/WebConfig.java`

- [ ] **Step 1: Create `JwtAuthFilter.java`**

The filter:
1. Skips public paths (register, login, refresh)
2. Extracts Bearer token from Authorization header
3. Verifies JWT using the appropriate domain (customer or admin based on constructor param)
4. Checks blacklist
5. Sets `userId` request attribute for downstream controllers

```java
package lab.gabon.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lab.gabon.common.ApiResponse;
import lab.gabon.common.AppError;
import lab.gabon.service.JwtService;
import lab.gabon.service.TokenStore;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.function.Function;

public class JwtAuthFilter extends OncePerRequestFilter {

    private final Function<String, DecodedJWT> verifier;
    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper;
    private final Set<String> publicPaths;

    public JwtAuthFilter(Function<String, DecodedJWT> verifier,
                         TokenStore tokenStore,
                         ObjectMapper objectMapper,
                         Set<String> publicPaths) {
        this.verifier = verifier;
        this.tokenStore = tokenStore;
        this.objectMapper = objectMapper;
        this.publicPaths = publicPaths;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getServletPath();
        if (publicPaths.stream().anyMatch(path::endsWith)) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeError(response, new AppError.Unauthorized());
            return;
        }

        try {
            DecodedJWT decoded = verifier.apply(header.substring(7));
            if (tokenStore.isBlacklisted(decoded.getId())) {
                writeError(response, new AppError.TokenInvalid());
                return;
            }
            request.setAttribute("userId", Long.parseLong(decoded.getSubject()));
            chain.doFilter(request, response);
        } catch (com.auth0.jwt.exceptions.TokenExpiredException e) {
            writeError(response, new AppError.TokenExpired());
        } catch (JWTVerificationException e) {
            writeError(response, new AppError.TokenInvalid());
        }
    }

    private void writeError(HttpServletResponse response, AppError error) throws IOException {
        response.setStatus(error.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(error));
    }
}
```

- [ ] **Step 2: Create `RateLimitFilter.java`**

Redis sliding window using sorted set (same algorithm as Go):

```java
package lab.gabon.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lab.gabon.common.ApiResponse;
import lab.gabon.common.AppError;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String group;
    private final int limit;
    private final Duration window;
    private final boolean useUserId;  // true = userId, false = IP

    public RateLimitFilter(StringRedisTemplate redis, ObjectMapper objectMapper,
                           String group, int limit, Duration window, boolean useUserId) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.group = group;
        this.limit = limit;
        this.window = window;
        this.useUserId = useUserId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = resolveKey(request);
        String redisKey = "rl:" + group + ":" + key;
        long nowMicro = System.currentTimeMillis() * 1000;
        double windowStart = (double) (nowMicro - window.toMillis() * 1000);

        var ops = redis.opsForZSet();
        ops.removeRangeByScore(redisKey, Double.NEGATIVE_INFINITY, windowStart);
        ops.add(redisKey, nowMicro + ":" + UUID.randomUUID().toString().substring(0, 8), nowMicro);
        Long count = ops.zCard(redisKey);
        redis.expire(redisKey, window.plusSeconds(1));

        if (count != null && count > limit) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(window.toSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(new AppError.RateLimited()));
            return;
        }
        chain.doFilter(request, response);
    }

    private String resolveKey(HttpServletRequest request) {
        if (useUserId) {
            Object userId = request.getAttribute("userId");
            if (userId != null) return userId.toString();
        }
        String xff = request.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }
}
```

- [ ] **Step 3: Create `WebConfig.java`**

Register JWT filters and rate limit filters for each URL pattern group:

```java
package lab.gabon.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lab.gabon.security.JwtAuthFilter;
import lab.gabon.security.RateLimitFilter;
import lab.gabon.service.JwtService;
import lab.gabon.service.TokenStore;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.Set;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    // ─── JWT Filters ─────────────────────────────────

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> customerJwtFilter(
            JwtService jwt, TokenStore store, ObjectMapper mapper) {
        var publicPaths = Set.of("/auth/register", "/auth/login", "/auth/refresh");
        var filter = new JwtAuthFilter(jwt::verifyCustomerAccess, store, mapper, publicPaths);
        var reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/v1/*");
        reg.setOrder(10);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> adminJwtFilter(
            JwtService jwt, TokenStore store, ObjectMapper mapper) {
        var publicPaths = Set.of("/auth/login", "/auth/refresh");
        var filter = new JwtAuthFilter(jwt::verifyAdminAccess, store, mapper, publicPaths);
        var reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/admin/v1/*");
        reg.setOrder(10);
        return reg;
    }

    // ─── Rate Limit Filters ──────────────────────────
    // Order: auth(5) → pub(6) → JWT(10) → user/admin(20)

    @Bean
    public FilterRegistrationBean<RateLimitFilter> authRateLimit(
            StringRedisTemplate redis, ObjectMapper mapper) {
        var filter = new RateLimitFilter(redis, mapper, "auth", 20, Duration.ofMinutes(1), false);
        var reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/v1/auth/*", "/admin/v1/auth/*");
        reg.setOrder(5);  // Before JWT filter
        return reg;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> publicRateLimit(
            StringRedisTemplate redis, ObjectMapper mapper) {
        // Public endpoints (unauthenticated): 120 req/min per IP
        var filter = new RateLimitFilter(redis, mapper, "pub", 120, Duration.ofMinutes(1), false);
        var reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/v1/*");
        reg.setOrder(6);  // Before JWT filter, catches unauthenticated requests
        return reg;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> apiRateLimit(
            StringRedisTemplate redis, ObjectMapper mapper) {
        // Authenticated user endpoints: 200 req/min per userId
        var filter = new RateLimitFilter(redis, mapper, "user", 200, Duration.ofMinutes(1), true);
        var reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/v1/*");
        reg.setOrder(20);  // After JWT filter (needs userId)
        return reg;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> adminRateLimit(
            StringRedisTemplate redis, ObjectMapper mapper) {
        var filter = new RateLimitFilter(redis, mapper, "admin", 200, Duration.ofMinutes(1), true);
        var reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/admin/v1/*");
        reg.setOrder(20);
        return reg;
    }
}
```

- [ ] **Step 4: Add health endpoint**

Create a simple health controller outside the filtered paths:

```java
package lab.gabon.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
```

- [ ] **Step 5: Verify: start app, hit health endpoint**

```bash
cd java && ./gradlew bootRun --no-daemon &
sleep 5
curl -s http://localhost:8082/health | jq .
# Expected: {"status":"ok"}
kill %1
```

- [ ] **Step 6: Commit**

```bash
git add java/src/main/java/lab/gabon/security/ java/src/main/java/lab/gabon/config/WebConfig.java java/src/main/java/lab/gabon/controller/HealthController.java
git commit -m "feat(java): add JWT auth filters + Redis rate limiting

Dual-domain JWT filters for /api/v1 and /admin/v1 paths.
Redis sliding-window rate limiting per route group.
Health endpoint at GET /health."
```

---

## Task 7: Client Auth Module

**Files:**
- Create: `java/src/main/java/lab/gabon/model/request/AuthRequests.java`
- Create: `java/src/main/java/lab/gabon/model/response/AuthResponses.java`
- Create: `java/src/main/java/lab/gabon/service/AuthService.java`
- Create: `java/src/main/java/lab/gabon/controller/api/AuthController.java`
- Create: `java/src/test/java/lab/gabon/service/AuthServiceTest.java`

- [ ] **Step 1: Write AuthService unit test (TDD: test first)**

```java
package lab.gabon.service;

import lab.gabon.common.AppException;
import lab.gabon.model.entity.Customer;
import lab.gabon.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock CustomerRepository customerRepo;
    @Mock JwtService jwtService;
    @Mock TokenStore tokenStore;
    @InjectMocks AuthService authService;

    @Test
    void register_success() {
        when(customerRepo.findByUsername("testuser")).thenReturn(Optional.empty());
        when(customerRepo.save(any())).thenAnswer(inv -> {
            // Simulate DB assigning ID
            return new Customer(1L, "testuser", "hash", null, null, null, null, null,
                    false, 0, null, null, null, null, null);
        });
        when(jwtService.issueCustomerTokens(1L, null))
                .thenReturn(new JwtService.TokenPair("access", "refresh", "family-id"));

        var result = authService.register("testuser", "Password1!");
        assertEquals("access", result.accessToken());
    }

    @Test
    void register_duplicateUsername_throws() {
        when(customerRepo.findByUsername("existing")).thenReturn(Optional.of(
                new Customer(1L, "existing", "hash", null, null, null, null, null,
                        false, 0, null, null, null, null, null)));

        assertThrows(AppException.class, () -> authService.register("existing", "Password1!"));
    }

    @Test
    void login_wrongPassword_throws() {
        var encoder = new BCryptPasswordEncoder();
        when(customerRepo.findByUsername("user")).thenReturn(Optional.of(
                new Customer(1L, "user", encoder.encode("correct"), null, null, null,
                        null, null, false, 0, null, null, null, null, null)));

        assertThrows(AppException.class, () -> authService.login("user", "wrong"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd java && ./gradlew test --tests "*AuthServiceTest" --no-daemon
```

Expected: FAIL (AuthService doesn't exist yet)

- [ ] **Step 3: Create request/response DTOs**

`AuthRequests.java`:
```java
package lab.gabon.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() {}

    public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(min = 6, max = 100) String password
    ) {}

    public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
    ) {}

    public record RefreshRequest(
        @NotBlank String refreshToken
    ) {}

    public record UpdatePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 6, max = 100) String newPassword
    ) {}
}
```

`AuthResponses.java`:
```java
package lab.gabon.model.response;

public final class AuthResponses {
    private AuthResponses() {}

    public record TokenPairResponse(String accessToken, String refreshToken) {}

    public record CustomerMeResponse(
        long id, String username, String name, String phone,
        String email, String avatarUrl, String signature,
        boolean isVip, long diamondBalance
    ) {}
}
```

- [ ] **Step 4: Implement `AuthService.java`**

```java
package lab.gabon.service;

import lab.gabon.common.AppError;
import lab.gabon.common.AppException;
import lab.gabon.model.entity.Customer;
import lab.gabon.model.response.AuthResponses.CustomerMeResponse;
import lab.gabon.model.response.AuthResponses.TokenPairResponse;
import lab.gabon.repository.CustomerRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthService {

    private final CustomerRepository customerRepo;
    private final JwtService jwtService;
    private final TokenStore tokenStore;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(CustomerRepository customerRepo, JwtService jwtService, TokenStore tokenStore) {
        this.customerRepo = customerRepo;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
    }

    public TokenPairResponse register(String username, String password) {
        customerRepo.findByUsername(username).ifPresent(c -> {
            throw new AppException(new AppError.UsernameExists());
        });
        // Pass null for createdAt/updatedAt — let DB DEFAULT NOW() handle timestamps
        var customer = customerRepo.save(new Customer(
                null, username, encoder.encode(password),
                null, null, null, null, null,
                false, 0, null, null,
                null, null, null));
        var tokens = jwtService.issueCustomerTokens(customer.id(), null);
        tokenStore.storeRefreshFamily(tokens.familyId(), customer.id(), extractJti(tokens.refreshToken()), false);
        return new TokenPairResponse(tokens.accessToken(), tokens.refreshToken());
    }

    public TokenPairResponse login(String username, String password) {
        var customer = customerRepo.findByUsername(username)
                .orElseThrow(() -> new AppException(new AppError.InvalidCredentials()));
        if (!encoder.matches(password, customer.passwordHash())) {
            throw new AppException(new AppError.InvalidCredentials());
        }
        customerRepo.updateLastLogin(customer.id(), Instant.now());
        var tokens = jwtService.issueCustomerTokens(customer.id(), null);
        tokenStore.storeRefreshFamily(tokens.familyId(), customer.id(), extractJti(tokens.refreshToken()), false);
        return new TokenPairResponse(tokens.accessToken(), tokens.refreshToken());
    }

    public TokenPairResponse refresh(String refreshToken) {
        var decoded = jwtService.verifyCustomerRefresh(refreshToken);
        String familyId = decoded.getClaim("family_id").asString();
        String oldJti = decoded.getId();
        long userId = Long.parseLong(decoded.getSubject());

        // Issue new tokens BEFORE CAS (if CAS fails, discard them)
        var newTokens = jwtService.issueCustomerTokens(userId, familyId);
        String newJti = extractJti(newTokens.refreshToken());

        long result = tokenStore.rotateRefreshToken(familyId, oldJti, newJti);
        if (result < 0) {
            // -1 = family expired, -2 = replay attack (family deleted)
            throw new AppException(new AppError.TokenInvalid());
        }
        return new TokenPairResponse(newTokens.accessToken(), newTokens.refreshToken());
    }

    public void logout(String accessToken, String refreshToken) {
        // Blacklist access token
        var accessDecoded = jwtService.verifyCustomerAccess(accessToken);
        tokenStore.blacklistAccessToken(accessDecoded.getId(), false);
        // Revoke refresh family
        try {
            var refreshDecoded = jwtService.verifyCustomerRefresh(refreshToken);
            tokenStore.revokeFamily(refreshDecoded.getClaim("family_id").asString());
        } catch (Exception ignored) {
            // Refresh token may already be expired, that's fine
        }
    }

    public CustomerMeResponse getMe(long customerId) {
        var c = customerRepo.findActiveById(customerId)
                .orElseThrow(() -> new AppException(new AppError.NotFound("customer not found")));
        return new CustomerMeResponse(c.id(), c.username(), c.name(), c.phone(),
                c.email(), c.avatarUrl(), c.signature(), c.isVip(), c.diamondBalance());
    }

    public void updatePassword(long customerId, String currentPassword, String newPassword) {
        var customer = customerRepo.findActiveById(customerId)
                .orElseThrow(() -> new AppException(new AppError.NotFound("customer not found")));
        if (!encoder.matches(currentPassword, customer.passwordHash())) {
            throw new AppException(new AppError.PasswordMismatch());
        }
        customerRepo.updatePassword(customerId, encoder.encode(newPassword));
    }

    private String extractJti(String token) {
        return com.auth0.jwt.JWT.decode(token).getId();
    }
}
```

- [ ] **Step 5: Implement `AuthController.java`**

```java
package lab.gabon.controller.api;

import jakarta.validation.Valid;
import lab.gabon.common.ApiResponse;
import lab.gabon.model.request.AuthRequests.*;
import lab.gabon.model.response.AuthResponses.*;
import lab.gabon.service.AuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<TokenPairResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok(authService.register(req.username(), req.password()));
    }

    @PostMapping("/login")
    public ApiResponse<TokenPairResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req.username(), req.password()));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.ok(authService.refresh(req.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String authHeader,
                                     @RequestBody RefreshRequest req) {
        String accessToken = authHeader.substring(7);
        authService.logout(accessToken, req.refreshToken());
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<CustomerMeResponse> me(@RequestAttribute("userId") long userId) {
        return ApiResponse.ok(authService.getMe(userId));
    }

    @PutMapping("/password")
    public ApiResponse<Void> updatePassword(@RequestAttribute("userId") long userId,
                                             @Valid @RequestBody UpdatePasswordRequest req) {
        authService.updatePassword(userId, req.currentPassword(), req.newPassword());
        return ApiResponse.ok();
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
cd java && ./gradlew test --tests "*AuthServiceTest" --no-daemon
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add java/src/main/java/lab/gabon/model/request/AuthRequests.java java/src/main/java/lab/gabon/model/response/AuthResponses.java java/src/main/java/lab/gabon/service/AuthService.java java/src/main/java/lab/gabon/controller/api/AuthController.java java/src/test/java/lab/gabon/service/AuthServiceTest.java
git commit -m "feat(java): add client auth module (register/login/refresh/logout/me/password)

TDD: AuthServiceTest covers register, duplicate username, wrong password.
AuthController maps 6 endpoints under /api/v1/auth."
```

---

## Task 8: Client Video Module

**Files:**
- Create: `java/src/main/java/lab/gabon/model/request/VideoRequests.java`
- Create: `java/src/main/java/lab/gabon/model/response/VideoResponses.java`
- Create: `java/src/main/java/lab/gabon/service/StorageService.java`
- Create: `java/src/main/java/lab/gabon/service/VideoService.java`
- Create: `java/src/main/java/lab/gabon/controller/api/VideoController.java`
- Create: `java/src/test/java/lab/gabon/service/VideoServiceTest.java`

- [ ] **Step 1: Write VideoService unit test**

Test key business logic: get video (not found, not approved), like (already liked, CTE returns 0), unlike.

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Create request/response DTOs**

`VideoRequests.java`: UploadUrlRequest (fileName, fileSize, mimeType), ConfirmUploadRequest (fileName, title, description, fileSize, mimeType, duration, width, height)

`VideoResponses.java`: VideoDetailResponse, VideoListItemResponse, UploadUrlResponse (uploadUrl, fileUrl)

- [ ] **Step 4: Implement `StorageService.java`**

S3 presigned URL generation using AWS SDK v2. Two methods: `generateVideoUploadUrl()` and `generateAvatarUploadUrl()`.

- [ ] **Step 5: Implement `VideoService.java`**

Key methods:
- `listVideos(page, pageSize)` — approved videos, paginated
- `listFeatured(page, pageSize)` — approved, ordered by like_count desc
- `listMyVideos(customerId, page, pageSize)` — user's own videos (any status)
- `getVideo(videoId, customerId)` — detail with `liked` boolean
- `generateUploadUrl(customerId, req)` — S3 presigned URL
- `confirmUpload(customerId, req)` — save video record (status=1 pending)
- `deleteVideo(videoId, customerId)` — soft delete (only owner)
- `likeVideo(videoId, customerId)` — CTE atomic like + count
- `unlikeVideo(videoId, customerId)` — CTE atomic unlike + count
- `recordPlayClick(videoId, customerId, ip)` — play_type=1
- `recordPlayValid(videoId, customerId, ip)` — play_type=2
- `listUserVideos(userId, page, pageSize)` — another user's approved videos

Critical concurrency: `likeVideo` uses the CTE repository method that returns 0 if already liked → throw `AlreadyLiked`.

- [ ] **Step 6: Implement `VideoController.java`**

Map all 12 video endpoints under `/api/v1/videos` and `/api/v1/users/{id}/videos`.

- [ ] **Step 7: Run tests, verify pass**

- [ ] **Step 8: Commit**

---

## Task 9: Client User Module

**Files:**
- Create: `java/src/main/java/lab/gabon/model/request/UserRequests.java`
- Create: `java/src/main/java/lab/gabon/model/response/UserResponses.java`
- Create: `java/src/main/java/lab/gabon/service/UserService.java`
- Create: `java/src/main/java/lab/gabon/controller/api/UserController.java`
- Create: `java/src/test/java/lab/gabon/service/UserServiceTest.java`

- [ ] **Step 1: Write UserService unit test**

Test: follow self (throws CannotFollowSelf), duplicate follow (AlreadyFollowing), unfollow non-following (NotFollowing).

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Create DTOs**

`UserRequests.java`: UpdateProfileRequest (name, phone, email, signature)
`UserResponses.java`: UserProfileResponse, FollowUserResponse (with followingCount, followerCount)

- [ ] **Step 4: Implement `UserService.java`**

Key methods:
- `getMyProfile(customerId)` / `updateMyProfile(customerId, req)`
- `generateAvatarUploadUrl(customerId)` / `confirmAvatarUpload(customerId, avatarUrl)`
- `getUserProfile(userId)`
- `follow(followerId, followedId)` — check self, check duplicate, atomic counter
- `unfollow(followerId, followedId)` — check exists, atomic counter
- `getFollowing(userId, page, pageSize)` / `getFollowers(userId, page, pageSize)`

- [ ] **Step 5: Implement `UserController.java`**

Map 8 endpoints under `/api/v1/users`.

- [ ] **Step 6: Run tests, verify pass**

- [ ] **Step 7: Commit**

---

## Task 10: Client Task + Activity Module

**Files:**
- Create: `java/src/main/java/lab/gabon/model/response/TaskResponses.java`
- Create: `java/src/main/java/lab/gabon/service/TaskService.java`
- Create: `java/src/main/java/lab/gabon/service/ActivityService.java`
- Create: `java/src/main/java/lab/gabon/controller/api/TaskController.java`
- Create: `java/src/main/java/lab/gabon/controller/api/ActivityController.java`
- Create: `java/src/test/java/lab/gabon/service/TaskServiceTest.java`

- [ ] **Step 1: Write TaskService unit test**

Test: claim task (not claimable — wrong status), period key generation (daily/weekly/monthly with Asia/Shanghai).

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Create DTOs**

`TaskResponses.java`: TaskItemResponse (taskId, taskCode, taskName, description, targetCount, currentCount, rewardDiamonds, status, periodKey), SignInResponse (date, streakDays)

- [ ] **Step 4: Implement `TaskService.java`**

Key methods:
- `listTasks(customerId)` — get task definitions + progress for current periods
- `claimReward(customerId, progressId)` — **transactional, uses FOR UPDATE**:
  1. `SELECT * FROM task_progress WHERE id = :id FOR UPDATE`
  2. Verify status = 2 (completed), customer matches
  3. Update status → 3 (claimed), set claimed_at
  4. `UPDATE customers SET diamond_balance = diamond_balance + :diamonds WHERE id = :customerId`
- `generatePeriodKey(taskType, now)` — Asia/Shanghai timezone:
  - Daily (1): `DateTimeFormatter.ofPattern("yyyy-MM-dd")`
  - Weekly (2): `DateTimeFormatter.ofPattern("yyyy-'W'ww")` (ISO week)
  - Monthly (3): `DateTimeFormatter.ofPattern("yyyy-MM")`

**Critical**: Always use `ZoneId.of("Asia/Shanghai")`, never JVM default timezone.

- [ ] **Step 5: Implement `ActivityService.java`**

`signIn(customerId)`:
1. Check today's record exists → throw AlreadySignedIn
2. Get yesterday's record for streak calculation
3. Insert sign_in_record (streak_days = yesterday.streak + 1 or 1)
4. Return SignInResponse

- [ ] **Step 6: Implement controllers**

`TaskController.java`: GET `/tasks`, POST `/tasks/{progressId}/claim`
`ActivityController.java`: POST `/activity/sign-in`

- [ ] **Step 7: Run tests, verify pass**

- [ ] **Step 8: Commit**

---

## Task 11: Admin Auth + Admin User Management

**Files:**
- Create: `java/src/main/java/lab/gabon/model/request/AdminRequests.java`
- Create: `java/src/main/java/lab/gabon/model/response/AdminResponses.java`
- Create: `java/src/main/java/lab/gabon/service/AdminService.java`
- Create: `java/src/main/java/lab/gabon/controller/admin/AdminAuthController.java`
- Create: `java/src/main/java/lab/gabon/controller/admin/AdminUserController.java`

- [ ] **Step 1: Create admin DTOs**

`AdminRequests.java`: CreateAdminRequest, UpdateAdminRequest, AdminLoginRequest, ResetPasswordRequest
`AdminResponses.java`: AdminUserResponse, AdminMeResponse

- [ ] **Step 2: Implement `AdminService.java`**

Admin auth methods (similar to customer auth but using admin JWT domain):
- `adminLogin(username, password)` — verify admin_users, issue admin tokens
- `adminRefresh(refreshToken)` — same CAS logic
- `adminLogout(accessToken, refreshToken)`
- `getAdminMe(adminId)`

Admin user management:
- `listAdminUsers(page, pageSize)`
- `getAdminUser(id)`
- `createAdminUser(req)` — hash password, insert admin_users
- `updateAdminUser(id, req)` — update fields
- `deleteAdminUser(id)` — soft delete
- `resetAdminPassword(id, newPassword)`

- [ ] **Step 3: Implement `AdminAuthController.java`**

POST `/admin/v1/auth/login`, POST `/admin/v1/auth/refresh`, POST `/admin/v1/auth/logout`, GET `/admin/v1/auth/me`

- [ ] **Step 4: Implement `AdminUserController.java`**

GET/POST `/admin/v1/admin-users`, GET/PUT/DELETE `/admin/v1/admin-users/{id}`, PUT `/admin/v1/admin-users/{id}/password`

- [ ] **Step 5: Verify compile and commit**

---

## Task 12: Admin Customer + Video Review + Reports

**Files:**
- Create: `java/src/main/java/lab/gabon/controller/admin/CustomerController.java`
- Create: `java/src/main/java/lab/gabon/controller/admin/VideoReviewController.java`
- Create: `java/src/main/java/lab/gabon/controller/admin/ReportController.java`

- [ ] **Step 1: Add admin methods to `AdminService.java`**

Customer management:
- `listCustomers(page, pageSize, search)` — paginated with optional search
- `resetCustomerPassword(customerId, newPassword)`

Video review:
- `listVideosForReview(page, pageSize, status)` — admin video list
- `getVideoForReview(videoId)` — detail for admin
- `reviewVideo(videoId, adminId, status, notes)` — approve/reject
- `adminDeleteVideo(videoId)` — soft delete

Reports:
- `getRevenueReport(startDate, endDate)` — aggregated diamond transactions
- `getDailyVideoReport(startDate, endDate)` — daily video upload counts
- `getVideoSummaryReport()` — total videos by status

- [ ] **Step 2: Implement 3 admin controllers**

`CustomerController.java`: GET `/admin/v1/customers`, PUT `/admin/v1/customers/{id}/password`
`VideoReviewController.java`: GET `/admin/v1/videos`, GET/DELETE `/admin/v1/videos/{id}`, POST `/admin/v1/videos/{id}/review`
`ReportController.java`: GET `/admin/v1/reports/revenue`, GET `/admin/v1/reports/video/daily`, GET `/admin/v1/reports/video/summary`

- [ ] **Step 3: Verify compile and commit**

---

## Task 13: Docker + Makefile + Benchmark Integration

**Files:**
- Create: `java/Dockerfile`
- Modify: `~/Makefile`
- Modify: `~/.env.example`
- Modify: `~/bench/correctness.sh`
- Modify: `~/bench/metrics.sh`
- Modify: `~/bench/oha-endpoints.sh`

- [ ] **Step 1: Create `Dockerfile`**

```dockerfile
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon
COPY src/ src/
RUN ./gradlew build -x test --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-Xmx512m", "-jar", "app.jar"]
```

- [ ] **Step 2: Add Java commands to `~/Makefile`**

Add the following section after the Kotlin section. Also update aggregates:

```makefile
# ─── Java ──────────────────────────────────────

dev-java:
	cd java && ./gradlew bootRun --no-daemon

build-java:
	cd java && ./gradlew build --no-daemon

test-java:
	cd java && ./gradlew test --no-daemon

lint-java:
	cd java && ./gradlew spotlessCheck

migrate-java:
	cd java && ./gradlew flywayMigrate
```

Update the `migrate` target:
```makefile
migrate: migrate-go migrate-rust migrate-java
```

Add to `.PHONY` and update the export line to include `JAVA_PORT`.

Add bench target:
```makefile
bench-k6-java:
	k6 run bench/k6-scenario.js --env BASE_URL=http://localhost:8082 --env PREFIX=/api/v1
```

- [ ] **Step 3: Add `JAVA_PORT=8082` to `.env.example`**

- [ ] **Step 4: Update `bench/correctness.sh`**

Add Java service detection and "Go vs Java" comparison section. Follow the exact pattern of "Go vs Kotlin" section, using `JAVA_BASE="http://localhost:8082"` and `JAVA_PREFIX="/api/v1"`.

- [ ] **Step 5: Update `bench/metrics.sh`**

Add `[Java]` section for each metric:
- LOC: `tokei java/src/ --sort code`
- Build: `cd java && ./gradlew clean --no-daemon; time ./gradlew build -x test --no-daemon`
- Binary: `du -h java/build/libs/*.jar`
- Deps: `cd java && ./gradlew dependencies --configuration runtimeClasspath --no-daemon | grep -c '--- '`
- Tests: `cd java && ./gradlew test --no-daemon | grep -oE '[0-9]+ tests'`

- [ ] **Step 6: Update `bench/oha-endpoints.sh`**

Add Java service detection (`JAVA_UP`) and token seeding for Java port 8082 with prefix `/api/v1`.

- [ ] **Step 7: Verify: run full app and correctness check against Go**

```bash
# Start both services
make dev-go &
make dev-java &
sleep 10
make bench-correctness
```

Expected: All "Go vs Java" checks pass.

- [ ] **Step 8: Commit**

```bash
git add java/Dockerfile Makefile .env.example bench/
git commit -m "feat(java): add Docker, Makefile integration, benchmark scripts

Multi-stage Dockerfile (JDK 25 build → JRE 25 runtime).
Makefile: dev/build/test/lint/migrate-java commands + bench-k6-java.
Updated correctness.sh, metrics.sh, oha-endpoints.sh for Java."
```

---

## Task 14: Concurrency Tests

**Files:**
- Create: `java/src/test/java/lab/gabon/ConcurrencyIT.java`

- [ ] **Step 1: Write 4 required concurrency tests**

Use Spring Boot Test + Testcontainers + `ExecutorService` with Virtual Threads:

```java
package lab.gabon;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class ConcurrencyIT {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired TestRestTemplate rest;

    // 1. Concurrent refresh: only one succeeds
    @Test
    void concurrentRefresh_onlyOneSucceeds() throws Exception {
        // Register a user, get refresh token
        // Launch N threads all calling POST /auth/refresh with the same token
        // Assert exactly 1 success, rest get TOKEN_INVALID
        int threads = 10;
        var latch = new CountDownLatch(1);
        var successes = new AtomicInteger(0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    latch.await();
                    // POST /api/v1/auth/refresh with the refresh token
                    // If 200 + code=OK → successes++
                    return null;
                });
            }
            latch.countDown();  // Release all threads
        }
        assertEquals(1, successes.get());
    }

    // 2. Logout then refresh fails
    @Test
    void logoutThenRefresh_fails() {
        // Register, logout, then try refresh
        // Assert refresh returns error
    }

    // 3. Concurrent likes: like_count increments by exactly 1
    @Test
    void concurrentLikes_countIncrementsOnce() throws Exception {
        // Create a video, launch N threads all liking it
        // Assert like_count = 1 (not N), exactly 1 success
    }

    // 4. Concurrent task claim: only one succeeds
    @Test
    void concurrentTaskClaim_onlyOneSucceeds() throws Exception {
        // Setup: create task definition + progress (status=completed)
        // Launch N threads all claiming the same progressId
        // Assert: 1 success, diamond_balance increased once
    }
}
```

**Implementation note:** Each test should set up its own data (register unique users, create video via API), then run concurrent operations using `CountDownLatch` to synchronize thread start.

- [ ] **Step 2: Run concurrency tests**

```bash
cd java && ./gradlew test --tests "*ConcurrencyIT" --no-daemon
```

Expected: All 4 pass.

- [ ] **Step 3: Run full test suite**

```bash
cd java && ./gradlew test --no-daemon
```

Expected: All tests pass.

- [ ] **Step 4: Run Spotless lint**

```bash
cd java && ./gradlew spotlessApply && ./gradlew spotlessCheck
```

- [ ] **Step 5: Final commit**

```bash
git add java/src/test/
git commit -m "test(java): add 4 required concurrency tests

CI gate: concurrent refresh (1 success), logout+refresh (fail),
concurrent likes (count=1), concurrent task claim (1 success).
All using Virtual Thread executor + CountDownLatch."
```

---

## Task 15: Create `java/CLAUDE.md`

**Files:**
- Create: `java/CLAUDE.md`

- [ ] **Step 1: Create `java/CLAUDE.md`**

Follow the structure of `go/CLAUDE.md` and `kotlin/CLAUDE.md`. Include:
- Project overview (Spring Boot 4.0 + JDK 25, part of gabon-lab)
- Tech stack table (with Go equivalents)
- Package structure
- Commands (dev, build, test, lint, migrate)
- Port and API prefix
- Key design decisions (Manual JWT, Spring Data JDBC, Virtual Threads, Sealed errors)
- Configuration (reads from `../.env`)
- Testing approach

- [ ] **Step 2: Commit**

```bash
git add java/CLAUDE.md
git commit -m "docs(java): add CLAUDE.md for Java implementation"
```

---

## Task 16: Update Repository-Level Documents

The old Java baseline (Maven, `com.gabon.*`, MySQL) is replaced. Repository docs must be updated to avoid conflicting instructions.

**Files:**
- Modify: `~/AGENTS.md`
- Modify: `~/CLAUDE.md`
- Modify: `~/.env.example`

- [ ] **Step 1: Update `AGENTS.md`**

Replace all references to the old Java setup:
- "Java uses Maven modules: gabon-service, gabon-admin, and gabon-common" → "Java uses Gradle single-module: `lab.gabon` package (Spring Boot 4.0 + JDK 25)"
- "Java is not wired into the root Makefile" → "Java is wired into the root Makefile: `make dev-java`, `make test-java`, `make lint-java`"
- "`com.gabon.*` package layout with controller/service/mapper" → "`lab.gabon.*` package layout with controller/service/repository (Spring Data JDBC)"
- "`cd java && mvn test`" → "`make test-java` or `cd java && ./gradlew test --no-daemon`"
- Add Kotlin references where missing (AGENTS.md currently only mentions Go/Rust/Java)

- [ ] **Step 2: Update root `CLAUDE.md`**

In the project CLAUDE.md:
- Update the `java/` entry in 仓库结构 from "Java 实现 (Spring Boot + MyBatis-Plus + MySQL)" to "Java 实现 (Spring Boot 4.0 + Spring Data JDBC + PostgreSQL)"
- Update 技术栈对比 table: Java column should reflect SB 4.0, Spring Data JDBC, PostgreSQL, Gradle
- Add `JAVA_PORT=8082` to port table
- Add `make dev-java / test-java / lint-java / migrate-java` to 常用命令
- Remove any MySQL references from Java row (Java now shares PostgreSQL)
- Update 评测结论速查 table with placeholder for new Java numbers

- [ ] **Step 3: Commit**

```bash
git add AGENTS.md CLAUDE.md
git commit -m "docs: update repo-level docs for new Java implementation

Replace old Java baseline (Maven, com.gabon.*, MySQL) with new
Spring Boot 4.0 + Gradle + lab.gabon.* + PostgreSQL references
in AGENTS.md and root CLAUDE.md."
```

---

## Post-Implementation Checklist

After all tasks are complete:

- [ ] Run `make dev-java` and verify health endpoint: `curl http://localhost:8082/health`
- [ ] Run `make test-java` — all tests pass
- [ ] Run `make lint-java` — no formatting issues
- [ ] Start Go + Java, run `make bench-correctness` — all "Go vs Java" checks pass
- [ ] Run `make bench-metrics` — Java metrics appear
- [ ] Update `docs/benchmark-report.md` with actual measured numbers
- [ ] Update CLAUDE.md at project root if needed (add Java port, commands)
- [ ] Create `java/CLAUDE.md` following the pattern of `go/CLAUDE.md` and `kotlin/CLAUDE.md`
