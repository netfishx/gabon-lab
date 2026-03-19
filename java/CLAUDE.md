# CLAUDE.md -- gabon-java

## Project Overview

Orbit short-video platform backend, Java implementation (Spring Boot 4.0). Part of the gabon-lab multi-language comparison (Java/Go/Rust/Kotlin), sharing infrastructure (PostgreSQL, Redis, Garage S3).

## Tech Stack

| Category | Choice | Go Equivalent |
|----------|--------|---------------|
| HTTP | Spring Boot 4.0 + Tomcat + Virtual Threads | Echo v4 |
| Database | Spring Data JDBC + PostgreSQL | pgx/v5 + sqlc |
| Migration | Flyway 11.8 (auto on startup + manual) | goose |
| Cache | Spring Data Redis (Lettuce) | go-redis/v9 |
| Auth | com.auth0:java-jwt + BCrypt | golang-jwt/v5 |
| Storage | AWS SDK v2 (S3) | AWS SDK v2 |
| Build | Gradle Kotlin DSL | go build |
| Lint | Spotless + google-java-format (2-space indent) | golangci-lint |
| Test | JUnit 5 + Mockito + Testcontainers | testify |

JDK 26, Spring Boot 4.0.3, google-java-format 1.35.0.

## Package Structure

```
src/main/java/lab/gabon/
  GabonApplication.java              -- Entry point, @SpringBootApplication
  config/
    AppConfig.java                   -- @ConfigurationProperties (JWT + S3 settings)
    RedisConfig.java                 -- RedisTemplate / Lettuce config
    WebConfig.java                   -- CORS, converters, etc.
  common/
    ApiResponse.java                 -- Unified response envelope (int code, message, data)
    AppError.java                    -- Sealed interface error hierarchy (19 error codes)
    AppException.java                -- Runtime exception wrapping AppError
    GlobalExceptionHandler.java      -- @ControllerAdvice: AppException -> HTTP response
    PageResponse.java                -- Paginated list wrapper
  model/
    entity/
      AdminUser.java                 -- Admin user entity
      Customer.java                  -- Customer entity
      CustomerSignInRecord.java      -- Daily sign-in records
      TaskDefinition.java            -- Task config (type, reward, period)
      TaskProgress.java              -- Per-user task progress
      UserFollow.java                -- Follow relationship
      Video.java                     -- Video entity
      VideoLike.java                 -- Like relationship
      VideoPlayRecord.java           -- Video play history
    request/
      AdminRequests.java             -- Admin request DTOs
      AuthRequests.java              -- Auth request DTOs (login, register, refresh)
      UserRequests.java              -- User profile request DTOs
      VideoRequests.java             -- Video request DTOs
    response/
      AdminResponses.java            -- Admin response DTOs
      AuthResponses.java             -- Token pair response
      TaskResponses.java             -- Task + sign-in response DTOs
      UserResponses.java             -- User profile responses
      VideoResponses.java            -- Video list/detail responses
  repository/
    AdminUserRepository.java         -- Admin user queries
    CustomerRepository.java          -- Customer CRUD
    CustomerSignInRecordRepository.java -- Sign-in record queries
    TaskDefinitionRepository.java    -- Task definition queries
    TaskProgressRepository.java      -- Task progress + reward claim
    UserFollowRepository.java        -- Follow/unfollow with atomic counters
    VideoLikeRepository.java         -- Like/unlike with atomic counters
    VideoPlayRecordRepository.java   -- Play history queries
    VideoRepository.java             -- Video CRUD + feed queries
  service/
    AuthService.java                 -- Register/login/refresh/logout
    JwtService.java                  -- Dual-domain JWT (customer/admin)
    TokenStore.java                  -- Redis token blacklist + refresh family
    VideoService.java                -- Video CRUD + presigned upload
    UserService.java                 -- Profile + avatar upload
    AdminService.java                -- Admin auth + video review + user management
    TaskService.java                 -- Task system + sign-in streaks
    ActivityService.java             -- Social interactions (follow/like)
    StorageService.java              -- S3 presigned URL generation
  security/
    JwtAuthFilter.java               -- Servlet filter: extract & verify JWT
    JwtDomain.java                   -- Domain constants (issuer/audience per realm)
    RateLimitFilter.java             -- Redis sliding-window rate limiter
  controller/
    HealthController.java            -- GET /health
    api/
      AuthController.java            -- /api/v1/auth/*
      VideoController.java           -- /api/v1/videos/*
      UserController.java            -- /api/v1/users/*
      TaskController.java            -- /api/v1/tasks/*, /api/v1/sign-in/*
      ActivityController.java        -- /api/v1/users/{id}/follow, /api/v1/videos/{id}/like
    admin/
      AdminAuthController.java       -- /admin/v1/auth/*
      AdminUserController.java       -- /admin/v1/admin-users/*
      CustomerController.java        -- /admin/v1/customers/*
      VideoReviewController.java     -- /admin/v1/videos/*
      ReportController.java          -- /admin/v1/reports/*
```

## Commands

```bash
./gradlew bootRun --no-daemon       # Dev server :8082
./gradlew test --no-daemon          # Tests
./gradlew build --no-daemon         # Full build (compile + test + check)
./gradlew spotlessCheck             # Lint check
./gradlew spotlessApply             # Lint fix
./gradlew flywayMigrate             # Manual migration
```

Or via Makefile (from repo root):

```bash
make dev-java                       # ./gradlew bootRun
make build-java                     # ./gradlew build
make test-java                      # ./gradlew test
make lint-java                      # ./gradlew spotlessCheck
make migrate-java                   # ./gradlew flywayMigrate
```

## Port and API Prefix

- Port: `8082` (env: `JAVA_PORT`)
- Customer API: `/api/v1/`
- Admin API: `/admin/v1/`
- Health check: `GET /health`

## Key Design Decisions

- **Spring DI (constructor injection)**: Standard `@Service` / `@Repository` beans, no manual wiring.
- **Sealed interface error hierarchy**: `AppError` sealed interface with 19 record variants. `GlobalExceptionHandler` maps `AppException` to HTTP responses.
- **int code response envelope**: `ApiResponse<T>(int code, String message, T data)` -- 0 = success, HTTP status = error. Matches Go/Rust/Kotlin pattern.
- **Flyway auto-migration**: Runs on startup (`spring.flyway.enabled=true`). Also available manually via `./gradlew flywayMigrate`.
- **Manual JWT (no Spring Security)**: `JwtAuthFilter` servlet filter + `JwtService` for fair benchmark comparison. Only `spring-security-crypto` for BCrypt.
- **Atomic counters**: SQL `SET col = col + 1` for like_count, follow counts, etc. Never read-modify-write.
- **CTE like/unlike**: Single statement ensures count update only when INSERT/DELETE succeeds.
- **FOR UPDATE task claim**: Transaction-scoped row lock for task reward claiming.
- **Redis Lua CAS**: Atomic compare-and-swap for refresh token rotation (issue first, CAS second, discard on failure).
- **Virtual Threads**: `spring.threads.virtual.enabled=true` -- Tomcat handles requests on virtual threads.
- **Route-scoped rate limiting**: Redis sliding-window via `RateLimitFilter`.
- **Dual JWT realms**: Customer (`iss=gabon-service, aud=customer`) and admin (`iss=gabon-admin, aud=admin`) use separate secrets.

## Configuration

Spring Boot `application.yml` reads env vars with defaults. Key variables:

- `JAVA_PORT` (default: 8082)
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` (PostgreSQL)
- `REDIS_URL` (default: `redis://:benchpass@localhost:6379/0`)
- `JWT_CUSTOMER_SECRET`, `JWT_ADMIN_SECRET`
- `JWT_CUSTOMER_ACCESS_TTL`, `JWT_CUSTOMER_REFRESH_TTL`
- `JWT_ADMIN_ACCESS_TTL`, `JWT_ADMIN_REFRESH_TTL`
- `JWT_CURRENT_KID`
- `S3_ENDPOINT`, `S3_REGION`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`
- `S3_BUCKET_VIDEOS`, `S3_BUCKET_AVATARS`

## Testing

| Layer | Approach |
|-------|----------|
| Service | JUnit 5 + Mockito mocked repos |
| Repository | Testcontainers (PostgreSQL + Flyway migrated) |
| Controller | MockMvc + mocked services |
| Integration | Full Spring context + Testcontainers |

**Must-cover concurrency tests** (same as Go/Rust/Kotlin):
- Concurrent refresh -- same old token, only one succeeds
- Logout then refresh -- must fail
- Concurrent likes -- like_count increments by exactly 1
- Concurrent task claim -- only one successful reward

## Critical Design Constraints

### Username Handling

Username uniqueness via partial index `LOWER(username) WHERE deleted_at IS NULL`. Register/login queries must use `LOWER(username)`.

### Period Key (Task System)

Business timezone fixed to `Asia/Shanghai`. Service layer provides unified period key generation:
- Daily: `2026-02-19`
- Weekly: `2026-W08` (ISO 8601, Monday start)
- Monthly: `2026-02`

### Soft Delete

`admin_users`, `customers`, `videos` use `deleted_at` soft delete. All queries must include `WHERE deleted_at IS NULL`.

### Auth Domain Isolation

Customer and admin tokens use different iss/aud/signing keys. Filter must verify all three:

| Domain | iss | aud | Secret env var |
|--------|-----|-----|----------------|
| Customer | gabon-service | customer | JWT_CUSTOMER_SECRET |
| Admin | gabon-admin | admin | JWT_ADMIN_SECRET |
