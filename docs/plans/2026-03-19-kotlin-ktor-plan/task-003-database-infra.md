# Task 003: Database infrastructure with Exposed and Virtual Threads

**type**: setup
**depends-on**: [001]

## Description

Set up the database layer: environment loading, application config, HikariCP connection pool, Exposed ORM tables, and a Virtual Thread-based coroutine dispatcher for blocking DB calls.

Key decisions:

- **AppConfig** (`config/AppConfig.kt`): implement a self-contained `.env` loader (no external library) that reads `../.env` file, parses `KEY=VALUE` lines (ignoring comments and blank lines), and populates a config object. Define `data class AppConfig` with nested sub-configs:
  - `port: Int` (from KOTLIN_PORT, default 8090)
  - `databaseUrl: String` (from DATABASE_URL)
  - `redisUrl: String` (from REDIS_URL)
  - `jwt: JwtConfig` — customerSecret (JWT_CUSTOMER_SECRET), customerAccessTtl (JWT_CUSTOMER_ACCESS_TTL, default "15m"), customerRefreshTtl (JWT_CUSTOMER_REFRESH_TTL, default "168h"), adminSecret (JWT_ADMIN_SECRET), adminAccessTtl (JWT_ADMIN_ACCESS_TTL), adminRefreshTtl (JWT_ADMIN_REFRESH_TTL), currentKid (JWT_CURRENT_KID)
  - `s3: S3Config` — endpoint (S3_ENDPOINT), region (S3_REGION), accessKey (S3_ACCESS_KEY), secretKey (S3_SECRET_KEY), bucketVideos (S3_BUCKET_VIDEOS), bucketAvatars (S3_BUCKET_AVATARS)
  Load once at startup and pass as a dependency.

- **Database** (`config/Database.kt`): create HikariDataSource from DATABASE_URL. Convert `postgres://user:pass@host:port/db` to `jdbc:postgresql://host:port/db` with separate username/password properties. Pool settings: maximumPoolSize=30, isAutoCommit=false, connectionTimeout=10s. Connect Exposed to the HikariDataSource via `Database.connect(dataSource)`. Define `val Dispatchers.Loom` using `Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()`. Define `suspend fun <T> dbQuery(block: Transaction.() -> T): T = withContext(Dispatchers.Loom) { transaction { block() } }` — this is the single entry point for all DB operations, ensuring blocking JDBC calls run on virtual threads.

- **Tables** (`repository/Tables.kt`): define Exposed Table objects matching the existing Go/Rust PostgreSQL schema exactly (source: `go/db/migrations/001_init.sql` + `003_add_sign_in.sql`). All tables:
  - `AdminUsers` — id (BIGSERIAL), username (VARCHAR 100), password_hash (VARCHAR 255), role (SMALLINT default 2), full_name (VARCHAR 255 nullable), phone (VARCHAR 50 nullable), avatar_url (VARCHAR 500 nullable), status (SMALLINT default 1), last_login_at (TIMESTAMPTZ nullable), created_at, updated_at, deleted_at (nullable)
  - `Customers` — id, username (VARCHAR 100), password_hash, name (VARCHAR 255 nullable), phone (VARCHAR 50 nullable), email (VARCHAR 255 nullable), avatar_url (VARCHAR 500 nullable), signature (VARCHAR 255 nullable), is_vip (BOOLEAN default false), diamond_balance (BIGINT default 0), withdrawal_password_hash (nullable), last_login_at (nullable), created_at, updated_at, deleted_at (nullable)
  - `Videos` — id, customer_id (FK→customers), title (VARCHAR 500 nullable), description (TEXT nullable), file_name (VARCHAR 255), file_size (BIGINT), file_url (VARCHAR 500), thumbnail_url (nullable), preview_gif_url (nullable), mime_type (VARCHAR 100), duration (INT nullable), width (INT nullable), height (INT nullable), status (SMALLINT default 1), review_notes (TEXT nullable), reviewed_by (FK→admin_users nullable), reviewed_at (nullable), total_clicks (BIGINT default 0), valid_clicks (BIGINT default 0), like_count (BIGINT default 0), created_at, updated_at, deleted_at (nullable)
  - `VideoPlayRecords` — id, video_id (FK), customer_id (FK nullable), play_type (SMALLINT), ip_address (VARCHAR 45 nullable), created_at
  - `VideoLikes` — id, video_id (FK), customer_id (FK), created_at, UNIQUE(video_id, customer_id)
  - `UserFollows` — id, follower_id (FK), followed_id (FK), created_at, UNIQUE(follower_id, followed_id), CHECK(follower_id != followed_id)
  - `TaskDefinitions` — id, task_code (VARCHAR 100 UNIQUE), task_name (VARCHAR 255), description (TEXT nullable), task_type (SMALLINT), task_category (SMALLINT), target_count (INT), reward_diamonds (INT), icon_url (nullable), display_order (INT default 0), vip_only (BOOLEAN default false), status (SMALLINT default 1), start_time (nullable), end_time (nullable), created_at, updated_at
  - `TaskProgress` — id, customer_id (FK), task_id (FK), current_count (INT default 0), target_count (INT), period_key (VARCHAR 50), task_status (SMALLINT default 1), reward_diamonds (INT), completed_at (nullable), claimed_at (nullable), created_at, updated_at, UNIQUE(customer_id, task_id, period_key)
  - `CustomerSignInRecords` — id, customer_id (FK), period_key (VARCHAR 50), reward_diamonds (INT), created_at, UNIQUE(customer_id, period_key)

  Column names use Exposed's snake_case mapping to match PG schema. All TIMESTAMPTZ columns use `timestampWithTimeZone()`. Partial unique indexes (LOWER(username) WHERE deleted_at IS NULL) are not modeled in Exposed — they already exist in the database from Go/Rust migrations.

## Files

- `kotlin/src/main/kotlin/lab/gabon/config/AppConfig.kt` — .env loader and AppConfig data class with all sub-configs
- `kotlin/src/main/kotlin/lab/gabon/config/Database.kt` — HikariCP setup, Exposed connection, Dispatchers.Loom, dbQuery helper
- `kotlin/src/main/kotlin/lab/gabon/repository/Tables.kt` — all Exposed table objects matching PG schema

## Verification

1. `./gradlew build` compiles without errors
2. With Docker infra running (`make up` from repo root), start the app — logs show successful PostgreSQL connection without errors
3. Verify HikariCP pool is active: logs show HikariPool initialization with maximumPoolSize=30
4. Verify Virtual Thread dispatcher: add a temporary `/db-test` endpoint that runs `dbQuery { AdminUsers.selectAll().count() }` and returns the count — should work without blocking Ktor's event loop
