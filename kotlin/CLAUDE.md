# CLAUDE.md -- gabon-kotlin

## Project Overview

Orbit short-video platform backend, Kotlin implementation. Part of the gabon-lab multi-language comparison (Java/Go/Rust/Kotlin), sharing infrastructure (PostgreSQL, Redis, Garage S3).

## Tech Stack

| Category | Choice | Go Equivalent |
|----------|--------|---------------|
| HTTP | Ktor 3.4.0 + Netty | Echo v4 |
| Database | Exposed 1.1.1 + HikariCP | pgx/v5 + sqlc |
| Migration | Flyway 11.8 (auto on startup) | goose (manual) |
| Cache | Lettuce 7.5 (coroutines) | go-redis/v9 |
| Auth | com.auth0:java-jwt + bcrypt | golang-jwt/v5 |
| Storage | AWS SDK Kotlin (S3) | AWS SDK v2 |
| Serialization | kotlinx-serialization | encoding/json |
| Coroutines | kotlinx-coroutines | goroutines |
| Logging | Logback (SLF4J) | slog |
| Build | Gradle + Shadow JAR | go build |

## Package Structure

```
src/main/kotlin/lab/gabon/
  Application.kt              -- Entry point, manual DI, embedded Netty server
  config/
    AppConfig.kt              -- Env loading (.env file + System.getenv)
    Database.kt               -- HikariCP + Exposed + Flyway auto-migrate
    Redis.kt                  -- Lettuce coroutine commands
  model/
    Request.kt                -- Request DTOs (@Serializable)
    Response.kt               -- JsonData<T> unified response wrapper
    Error.kt                  -- Sealed AppError hierarchy
    Constants.kt              -- Error codes, pagination defaults
  repository/
    Tables.kt                 -- Exposed table definitions
    CustomerRepo.kt           -- Customer CRUD
    VideoRepo.kt              -- Video queries
    SocialRepo.kt             -- Follow/like with atomic counters
    AdminUserRepo.kt          -- Admin user management
    AdminVideoRepo.kt         -- Admin video review
    TaskRepo.kt               -- Task + reward logic
    SignInRepo.kt             -- Daily sign-in records
    PlayRecordRepo.kt         -- Video play history
    ReportRepo.kt             -- Content reports
  service/
    JwtService.kt             -- Dual-domain JWT (customer/admin)
    TokenStore.kt             -- Redis token blacklist + refresh family
    AuthService.kt            -- Register/login/refresh/logout
    VideoService.kt           -- Video CRUD + presigned upload
    SocialService.kt          -- Follow/unfollow, like/unlike
    UserService.kt            -- Profile + avatar upload
    AdminService.kt           -- Admin auth + video review + user management
    TaskService.kt            -- Task system + sign-in streaks
    ReportService.kt          -- Content report management
    StorageService.kt         -- S3 presigned URL generation
  route/
    AuthRoutes.kt             -- /api/v1/auth/*
    VideoRoutes.kt            -- /api/v1/videos/*
    SocialRoutes.kt           -- /api/v1/users/{id}/follow, /api/v1/videos/{id}/like
    UserRoutes.kt             -- /api/v1/users/me/*
    TaskRoutes.kt             -- /api/v1/tasks/*, /api/v1/sign-in/*
    AdminRoutes.kt            -- /admin/v1/*
    ReportRoutes.kt           -- /admin/v1/reports/*
  plugin/
    Serialization.kt          -- kotlinx.serialization JSON config
    ErrorHandling.kt          -- StatusPages: AppError -> HTTP response
    Authentication.kt         -- Ktor JWT auth (customer + admin realms)
    RateLimit.kt              -- Redis sliding-window rate limiter
    Routing.kt                -- Route tree assembly
```

## Commands

```bash
./gradlew run --no-daemon           # Dev server on port 8090
./gradlew test --no-daemon          # Run all tests
./gradlew shadowJar --no-daemon     # Build fat JAR -> build/libs/gabon-api.jar
./gradlew build --no-daemon         # Compile + test + check
./gradlew clean --no-daemon         # Clean build outputs
```

Or via Makefile:

```bash
make dev       # ./gradlew run
make build     # ./gradlew buildFatJar (alias for shadowJar)
make test      # ./gradlew test
make lint      # ./gradlew build
make clean     # ./gradlew clean
```

## Port and API Prefix

- Port: `8090` (env: `KOTLIN_PORT`)
- Customer API: `/api/v1/`
- Admin API: `/admin/v1/`
- Health check: `GET /health`

## Key Design Decisions

- **Manual DI**: No framework (Koin/Dagger). Dependencies wired in `Application.kt main()`.
- **Sealed error hierarchy**: `AppError` sealed class with typed subclasses -> `StatusPages` maps to HTTP codes.
- **Flyway auto-migration**: Runs on startup via `Database.kt`, no manual migrate step needed.
- **Atomic counters**: Like/follow counts use SQL `SET col = col + 1`, never read-modify-write.
- **Route-scoped rate limiting**: Redis sliding-window, configurable per route group.
- **Dual JWT realms**: Customer and admin tokens use separate secrets, issuers, and audiences.
- **Coroutine-first Redis**: Lettuce coroutine API for non-blocking cache operations.

## Configuration

Reads from `../.env` file (project root) and falls back to `System.getenv()`. Key variables:

- `KOTLIN_PORT` (default: 8090)
- `DATABASE_URL` (PostgreSQL)
- `REDIS_URL` (Redis with password)
- `JWT_CUSTOMER_SECRET`, `JWT_ADMIN_SECRET`
- `S3_ENDPOINT`, `S3_REGION`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`

## Testing

Uses Ktor `testApplication` with embedded test engine. Mock dependencies via MockK.

| Layer | Approach |
|-------|----------|
| Repository | Exposed + test DB (Flyway migrated) |
| Service | MockK mocked repos |
| Route | testApplication + MockK services |
