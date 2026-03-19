# Task 003: Database infrastructure with Exposed and Virtual Threads

**type**: setup
**depends-on**: [001]

## Description

Set up the database layer: environment loading, application config, HikariCP connection pool, Exposed ORM tables, and a Virtual Thread-based coroutine dispatcher for blocking DB calls.

Key decisions:

- **AppConfig** (`config/AppConfig.kt`): implement a self-contained `.env` loader (no external library) that reads `../.env` file, parses `KEY=VALUE` lines (ignoring comments and blank lines), and populates a config object. Define `data class AppConfig` with nested sub-configs:
  - `port: Int` (from PORT, default 8090)
  - `databaseUrl: String` (from DATABASE_URL)
  - `redisUrl: String` (from REDIS_URL)
  - `jwt: JwtConfig` (customerSecret, adminSecret, accessTtl, refreshTtl from JWT_CUSTOMER_SECRET, JWT_ADMIN_SECRET, etc.)
  - `s3: S3Config` (endpoint, accessKey, secretKey, region from S3_ENDPOINT, S3_ACCESS_KEY_ID, S3_SECRET_ACCESS_KEY, S3_REGION)
  Load once at startup and pass as a dependency.

- **Database** (`config/Database.kt`): create HikariDataSource from DATABASE_URL. Convert `postgres://user:pass@host:port/db` to `jdbc:postgresql://host:port/db` with separate username/password properties. Pool settings: maximumPoolSize=30, isAutoCommit=false, connectionTimeout=10s. Connect Exposed to the HikariDataSource via `Database.connect(dataSource)`. Define `val Dispatchers.Loom` using `Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()`. Define `suspend fun <T> dbQuery(block: Transaction.() -> T): T = withContext(Dispatchers.Loom) { transaction { block() } }` — this is the single entry point for all DB operations, ensuring blocking JDBC calls run on virtual threads.

- **Tables** (`repository/Tables.kt`): define Exposed Table objects matching the existing Go/Rust PostgreSQL schema exactly. All tables:
  - `AdminUsers` — id (long autoIncrement), username (varchar unique), passwordHash (varchar), role (varchar), createdAt (timestamp), updatedAt (timestamp)
  - `Customers` — id, phone (unique), passwordHash, nickname, avatarUrl (nullable), coin, createdAt, updatedAt
  - `Videos` — id, customerId (FK), title, coverUrl, videoUrl, duration, status (int), likeCount, viewCount, createdAt, updatedAt
  - `VideoPlayRecords` — id, customerId, videoId, playedAt
  - `VideoLikes` — id, customerId, videoId, createdAt (with unique constraint on customerId+videoId)
  - `UserFollows` — id, followerId, followeeId, createdAt (with unique constraint on followerId+followeeId)
  - `TaskDefinitions` — id, taskType (varchar unique), title, description, rewardCoin, dailyLimit, isActive (bool), createdAt, updatedAt
  - `TaskProgress` — id, customerId, taskDefinitionId, completedAt (date), completedCount, rewardedCount, createdAt, updatedAt (with unique constraint on customerId+taskDefinitionId+completedAt)
  - `CustomerSignInRecords` — id, customerId, signInDate (date), rewardCoin, createdAt

  Column names should use Exposed's snake_case mapping to match the existing PG schema (e.g., `password_hash`, `avatar_url`, `like_count`).

## Files

- `kotlin/src/main/kotlin/lab/gabon/config/AppConfig.kt` — .env loader and AppConfig data class with all sub-configs
- `kotlin/src/main/kotlin/lab/gabon/config/Database.kt` — HikariCP setup, Exposed connection, Dispatchers.Loom, dbQuery helper
- `kotlin/src/main/kotlin/lab/gabon/repository/Tables.kt` — all Exposed table objects matching PG schema

## Verification

1. `./gradlew build` compiles without errors
2. With Docker infra running (`make up` from repo root), start the app — logs show successful PostgreSQL connection without errors
3. Verify HikariCP pool is active: logs show HikariPool initialization with maximumPoolSize=30
4. Verify Virtual Thread dispatcher: add a temporary `/db-test` endpoint that runs `dbQuery { AdminUsers.selectAll().count() }` and returns the count — should work without blocking Ktor's event loop
