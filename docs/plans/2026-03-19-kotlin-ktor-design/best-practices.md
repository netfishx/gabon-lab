# Kotlin/Ktor Backend Best Practices

> Tech Stack: Ktor 3.4.0 + Kotlin 2.3.20 + Netty, Exposed 1.1.1, kotlinx-serialization 1.10.0,
> Lettuce 7.3.0, JDK 21+ Virtual Threads, Generational ZGC, AWS SDK for Kotlin (S3)
>
> Benchmark targets: Java (88K QPS / 354MB), Go (96K QPS / 43MB), Rust (182K QPS / 19MB)

---

## 1. Performance Optimization

### 1.1 Ktor Netty Engine Tuning

```kotlin
fun main() {
    embeddedServer(Netty, port = 8081, configure = {
        // Worker group threads — default is Runtime.availableProcessors() * 2.
        // For IO-heavy REST APIs with Virtual Threads handling blocking,
        // keep Netty workers lean (event loop only).
        workerGroupSize = Runtime.getRuntime().availableProcessors()

        // Connection queue (TCP backlog) for burst traffic during benchmarks
        connectionGroupSize = Runtime.getRuntime().availableProcessors()

        // Channel pipeline tuning
        channelPipelineConfig = {
            addLast("idle", io.netty.handler.timeout.IdleStateHandler(60, 30, 0))
        }

        // Response write timeout — fail fast on stuck connections
        responseWriteTimeoutSeconds = 30
    }) {
        module()
    }.start(wait = true)
}
```

**Key decisions:**
- Keep Netty event loop threads equal to CPU cores (not 2x) since blocking IO goes to Virtual Threads.
- Enable HTTP/2 cleartext (h2c) only if bench clients support it — most benchmarks use HTTP/1.1.
- Do NOT set `callGroupSize` — with Virtual Threads, the default Dispatchers.Default is sufficient.

### 1.2 Virtual Threads Dispatcher

Virtual Threads are the key to competing with Go's goroutine performance. Configure Ktor to use them:

```kotlin
// application.kt
fun Application.module() {
    // Use Virtual Threads as the default dispatcher for all route handlers.
    // This eliminates the coroutine-thread mismatch for blocking JDBC/Redis calls.
    val vtDispatcher = Dispatchers.IO.limitedParallelism(
        parallelism = Int.MAX_VALUE, // Virtual Threads don't need a ceiling
        name = "vt-io"
    )

    // Or use the JDK 21+ executor directly:
    val vtExecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
    val vtDispatcher2 = vtExecutor.asCoroutineDispatcher()

    // Install the dispatcher globally via plugin
    install(createApplicationPlugin(name = "VirtualThreadDispatcher") {
        onCall { call ->
            withContext(vtDispatcher) {
                // All route handlers run on Virtual Threads
            }
        }
    })
}
```

**Simpler approach** — just wrap blocking calls at the call site:

```kotlin
fun Route.videoRoutes(videoService: VideoService) {
    get("/api/videos") {
        // Exposed transactions are blocking JDBC under the hood.
        // withContext(Dispatchers.IO) on JDK 21+ will use Virtual Threads
        // if -Djdk.virtualThreadScheduler.parallelism is set.
        val videos = withContext(Dispatchers.IO) {
            videoService.listVideos(call.queryParameters)
        }
        call.respond(ApiResponse.ok(videos))
    }
}
```

### 1.3 HikariCP Pool Sizing for Virtual Threads

With platform threads, the classic formula is `connections = cores * 2 + spindle_count`. With Virtual Threads, thousands of coroutines can run concurrently, so the database connection pool becomes the bottleneck instead:

```kotlin
val hikariConfig = HikariConfig().apply {
    jdbcUrl = "jdbc:postgresql://localhost:5432/gabon_lab"
    driverClassName = "org.postgresql.Driver"
    username = "postgres"
    password = "postgres"

    // Virtual Threads: many concurrent tasks will block waiting for a connection.
    // Size the pool to match PostgreSQL's effective parallel query capacity.
    // For a benchmark machine with 8 cores, 20-30 is the sweet spot.
    maximumPoolSize = 30       // Much larger than platform-thread default of 10
    minimumIdle = 10           // Keep warm connections ready

    // With Virtual Threads, tasks are cheap — they can afford to wait longer
    // for a connection rather than failing fast.
    connectionTimeout = 10_000  // 10s (up from default 30s is unnecessary)

    // Leak detection: Virtual Threads make leaks harder to spot
    // because thread dumps look different.
    leakDetectionThreshold = 30_000

    // PostgreSQL-specific optimizations
    addDataSourceProperty("preparedStatementCacheQueries", "256")
    addDataSourceProperty("preparedStatementCacheSizeMiB", "5")

    // CRITICAL: Disable autocommit — Exposed manages transactions explicitly.
    isAutoCommit = false

    // Use READ_COMMITTED for most REST API workloads.
    transactionIsolation = "TRANSACTION_READ_COMMITTED"
}

val dataSource = HikariDataSource(hikariConfig)
Database.connect(dataSource)
```

**Why 30 connections, not 100?**

PostgreSQL has a maximum effective concurrency that depends on CPU, shared_buffers, and IO subsystem. Under benchmark load, going beyond ~30 connections on an 8-core machine causes lock contention and context switching in PostgreSQL itself. The Virtual Thread tasks will park efficiently while waiting for a connection from the pool.

### 1.4 Exposed DSL Performance Pitfalls

```kotlin
// Table definition
object Videos : LongIdTable("videos") {
    val title = varchar("title", 200)
    val coverUrl = varchar("cover_url", 500)
    val videoUrl = varchar("video_url", 500)
    val status = enumerationByName<VideoStatus>("status", 20)
    val likeCount = integer("like_count").default(0)
    val customerId = long("customer_id").references(Customers.id)
    val createdAt = timestamp("created_at")
}

// PITFALL 1: N+1 queries — Exposed DSL doesn't auto-join like DAO
// BAD: fetching customer for each video separately
transaction {
    Videos.selectAll().map { row ->
        val customer = Customers.selectAll()  // N+1!
            .where { Customers.id eq row[Videos.customerId] }
            .single()
        toVideoDTO(row, customer)
    }
}

// GOOD: explicit join
transaction {
    (Videos innerJoin Customers)
        .selectAll()
        .where { Videos.status eq VideoStatus.APPROVED }
        .orderBy(Videos.createdAt, SortOrder.DESC)
        .limit(20)
        .map { toVideoDTO(it) }
}

// PITFALL 2: Unnecessary column selection — always select only needed columns
// BAD: selectAll() when you only need id + title
// GOOD:
transaction {
    Videos.select(Videos.id, Videos.title, Videos.coverUrl)
        .where { Videos.status eq VideoStatus.APPROVED }
        .map { VideoListItem(it[Videos.id].value, it[Videos.title], it[Videos.coverUrl]) }
}

// PITFALL 3: Atomic counter updates — never read-modify-write
// BAD:
transaction {
    val current = Videos.select(Videos.likeCount)
        .where { Videos.id eq videoId }.single()[Videos.likeCount]
    Videos.update({ Videos.id eq videoId }) {
        it[likeCount] = current + 1  // Race condition!
    }
}

// GOOD: atomic SQL increment
transaction {
    Videos.update({ Videos.id eq videoId }) {
        with(SqlExpressionBuilder) {
            it.update(likeCount, likeCount + 1)
        }
    }
}
```

**When to use raw SQL via `exec()`:**

```kotlin
// Complex CTE for atomic like + increment (matching Go/Rust implementation)
transaction {
    val sql = """
        WITH inserted AS (
            INSERT INTO video_likes (video_id, customer_id)
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
            RETURNING video_id
        )
        UPDATE videos SET like_count = like_count + 1
        WHERE id = (SELECT video_id FROM inserted)
        RETURNING like_count
    """.trimIndent()

    exec(sql, listOf(
        LongColumnType() to videoId,
        LongColumnType() to customerId,
    )) { rs ->
        if (rs.next()) rs.getInt("like_count") else null
    }
}
```

Use raw SQL when:
- CTEs (WITH ... AS) are involved
- Window functions needed
- RETURNING clauses on INSERT/UPDATE
- Bulk upsert with ON CONFLICT
- Exposed DSL generates suboptimal queries (check SQL logging)

### 1.5 kotlinx-serialization vs Jackson

kotlinx-serialization wins on both startup and steady-state throughput for this workload:

| Metric | kotlinx-serialization | Jackson |
|--------|----------------------|---------|
| Compile-time safety | Yes (generated serializers) | No (reflection at runtime) |
| Cold start impact | Near zero | Reflection warmup ~200ms |
| Throughput (small JSON) | ~15% faster | Baseline |
| Native Image compat | Excellent | Requires reflection config |

```kotlin
// Global Json instance — reuse for all serialization
val AppJson = Json {
    ignoreUnknownKeys = true       // Forward compatibility with API changes
    encodeDefaults = false         // Smaller payloads — omit default values
    explicitNulls = false          // Omit null fields entirely
    isLenient = false              // Strict parsing in production
    coerceInputValues = true       // Coerce null to default for non-nullable
}

// Install in Ktor
install(ContentNegotiation) {
    json(AppJson)
}
```

### 1.6 JVM Flags for REST API Workload

```bash
# Production JVM flags for benchmarking
JAVA_OPTS="-server \
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -Xms256m \
  -Xmx512m \
  -XX:+AlwaysPreTouch \
  -XX:+UseStringDeduplication \
  -XX:+OptimizeStringConcat \
  -Djdk.virtualThreadScheduler.parallelism=0 \
  -Djdk.virtualThreadScheduler.maxPoolSize=256 \
  --enable-preview"
```

**Flag explanations:**

| Flag | Purpose |
|------|---------|
| `-XX:+UseZGC -XX:+ZGenerational` | Generational ZGC: sub-ms pause times, crucial for p99 latency |
| `-Xms256m -Xmx512m` | Fixed heap to avoid resize pauses. 512MB targets Go-like memory |
| `-XX:+AlwaysPreTouch` | Pre-fault heap pages at startup — avoids page faults under load |
| `virtualThreadScheduler.parallelism=0` | Auto-detect CPU cores for VT scheduler (default) |
| `virtualThreadScheduler.maxPoolSize=256` | Cap platform threads backing VTs |

**Benchmark-specific flags (not for production):**

```bash
# Aggressive JIT for benchmarks — longer warmup, higher peak throughput
JAVA_OPTS="$JAVA_OPTS \
  -XX:+TieredCompilation \
  -XX:CompileThreshold=1000 \
  -XX:-UseCompressedOops"  # Slightly faster on machines with >32GB RAM
```

---

## 2. Kotlin Idioms

### 2.1 Sealed Class Error Handling

Mirror the Rust `AppError` / Go `AppError` pattern from the sibling implementations:

```kotlin
import io.ktor.http.*

sealed class AppError(
    val code: String,
    val status: HttpStatusCode,
    override val message: String,
) : RuntimeException(message) {

    // --- Auth ---
    data class InvalidCredentials(
        override val message: String = "invalid credentials",
    ) : AppError("AUTH_INVALID_CREDENTIALS", HttpStatusCode.Unauthorized, message)

    data class TokenExpired(
        override val message: String = "token expired",
    ) : AppError("AUTH_TOKEN_EXPIRED", HttpStatusCode.Unauthorized, message)

    data class TokenInvalid(
        override val message: String = "invalid token",
    ) : AppError("AUTH_TOKEN_INVALID", HttpStatusCode.Unauthorized, message)

    data class Forbidden(
        override val message: String = "forbidden",
    ) : AppError("FORBIDDEN", HttpStatusCode.Forbidden, message)

    // --- Business ---
    data class NotFound(
        val resource: String,
        override val message: String = "$resource not found",
    ) : AppError("NOT_FOUND", HttpStatusCode.NotFound, message)

    data class Conflict(
        override val message: String,
    ) : AppError("CONFLICT", HttpStatusCode.Conflict, message)

    data class BadRequest(
        override val message: String,
    ) : AppError("BAD_REQUEST", HttpStatusCode.BadRequest, message)

    // --- Infrastructure (message never leaks to client) ---
    data class Internal(
        val cause: Throwable? = null,
        override val message: String = "internal server error",
    ) : AppError("INTERNAL_ERROR", HttpStatusCode.InternalServerError, "internal server error")
}
```

**Usage in service layer** — return `Result<T>` instead of throwing:

```kotlin
class AuthService(
    private val customerRepo: CustomerRepository,
    private val jwtService: JwtService,
    private val redis: RedisCommands<String, String>,
) {
    fun login(username: String, password: String): Result<TokenPair> {
        val customer = customerRepo.findByUsername(username.lowercase())
            ?: return Result.failure(AppError.InvalidCredentials())

        if (!BCrypt.checkpw(password, customer.passwordHash)) {
            return Result.failure(AppError.InvalidCredentials())
        }

        val pair = jwtService.generateCustomerTokens(customer.id)
        return Result.success(pair)
    }
}
```

**Map to HTTP response in a single place:**

```kotlin
// Install a StatusPages handler that catches AppError
install(StatusPages) {
    exception<AppError> { call, err ->
        // Internal/Database errors — log the real cause, send generic message
        if (err is AppError.Internal) {
            call.application.log.error("Internal error", err.cause)
        }
        call.respond(err.status, ApiResponse.error(err.code, err.message))
    }
    exception<Throwable> { call, err ->
        call.application.log.error("Unhandled exception", err)
        call.respond(
            HttpStatusCode.InternalServerError,
            ApiResponse.error("INTERNAL_ERROR", "internal server error"),
        )
    }
}
```

### 2.2 Extension Functions for Route Organization

```kotlin
// routes/VideoRoutes.kt
fun Route.videoRoutes(videoService: VideoService) {
    route("/api/videos") {
        get { handleListVideos(videoService) }
        get("/{id}") { handleGetVideo(videoService) }

        // Authenticated routes
        authenticate("customer-jwt") {
            post { handleCreateVideo(videoService) }
            post("/{id}/like") { handleLikeVideo(videoService) }
            delete("/{id}/like") { handleUnlikeVideo(videoService) }
        }
    }
}

fun Route.adminVideoRoutes(adminService: AdminService) {
    route("/admin/videos") {
        authenticate("admin-jwt") {
            get { handleAdminListVideos(adminService) }
            patch("/{id}/status") { handleUpdateVideoStatus(adminService) }
        }
    }
}

// routes/AuthRoutes.kt
fun Route.authRoutes(authService: AuthService) {
    route("/api/auth") {
        post("/register") { handleRegister(authService) }
        post("/login") { handleLogin(authService) }

        authenticate("customer-jwt") {
            get("/me") { handleMe(authService) }
            post("/refresh") { handleRefresh(authService) }
            post("/logout") { handleLogout(authService) }
        }
    }
}

// Application module — compose all routes
fun Application.configureRouting(deps: Dependencies) {
    routing {
        videoRoutes(deps.videoService)
        authRoutes(deps.authService)
        adminVideoRoutes(deps.adminService)
        healthRoutes()
    }
}
```

### 2.3 Coroutine Scope Management

```kotlin
// Ktor manages coroutine lifecycle per request. Don't create your own scope.

// WRONG: launching unstructured coroutines
get("/api/videos") {
    GlobalScope.launch { /* fire and forget — will leak */ }
}

// CORRECT: use the call's coroutine scope
get("/api/videos") {
    // This coroutine is automatically cancelled if the client disconnects
    val videos = withContext(Dispatchers.IO) {
        videoService.listVideos()
    }
    call.respond(ApiResponse.ok(videos))
}

// For parallel fetches within a request:
get("/api/dashboard") {
    coroutineScope {
        val stats = async(Dispatchers.IO) { statsService.getStats() }
        val recent = async(Dispatchers.IO) { videoService.getRecent(10) }
        call.respond(ApiResponse.ok(DashboardData(stats.await(), recent.await())))
    }
}

// Background tasks — use application-scoped lifecycle
fun Application.configureBackgroundTasks() {
    val job = launch {
        while (isActive) {
            delay(60_000)
            cleanupExpiredTokens()
        }
    }
    monitor.subscribe(ApplicationStopping) {
        job.cancel()
    }
}
```

### 2.4 DTO Patterns with @Serializable

```kotlin
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// Unified API response format — matches Go/Rust sibling implementations
@Serializable
data class ApiResponse<T>(
    val code: String,
    val message: String,
    val data: T? = null,
) {
    companion object {
        fun <T> ok(data: T, message: String = "ok") =
            ApiResponse(code = "OK", message = message, data = data)

        fun error(code: String, message: String) =
            ApiResponse<Nothing>(code = code, message = message, data = null)
    }
}

// Request DTOs — validate at the boundary
@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val nickname: String? = null,
) {
    fun validate(): List<String> = buildList {
        if (username.length !in 3..32) add("username must be 3-32 characters")
        if (password.length < 8) add("password must be at least 8 characters")
        if (nickname != null && nickname.length > 50) add("nickname too long")
    }
}

// Response DTOs — use @SerialName for snake_case wire format
@Serializable
data class VideoResponse(
    val id: Long,
    val title: String,
    @SerialName("cover_url") val coverUrl: String,
    @SerialName("like_count") val likeCount: Int,
    @SerialName("created_at") val createdAt: String,
)

// Or configure globally with naming strategy (kotlinx-serialization 1.7+):
val AppJson = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
}

// Pagination — reusable wrapper
@Serializable
data class PageResponse<T>(
    val items: List<T>,
    val total: Long,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
) {
    @SerialName("has_more")
    val hasMore: Boolean get() = page.toLong() * pageSize < total
}
```

### 2.5 Null Safety Patterns

```kotlin
// NEVER use !! — it throws NPE which is a Java antipattern in Kotlin

// BAD:
val customerId = call.principal<JWTPrincipal>()!!.payload.getClaim("sub").asLong()

// GOOD: fail with a meaningful domain error
val principal = call.principal<JWTPrincipal>()
    ?: throw AppError.TokenInvalid("missing principal")
val customerId = principal.payload.getClaim("sub")?.asLong()
    ?: throw AppError.TokenInvalid("missing subject claim")

// For path parameters (always present if route matched):
val videoId = call.parameters["id"]?.toLongOrNull()
    ?: throw AppError.BadRequest("invalid video id")

// For optional query parameters with defaults:
val page = call.queryParameters["page"]?.toIntOrNull() ?: 1
val pageSize = call.queryParameters["page_size"]?.toIntOrNull()?.coerceIn(1..100) ?: 20

// Chained null-safe access:
val avatarUrl = customer.profile?.avatar?.url ?: DEFAULT_AVATAR_URL
```

### 2.6 When to Use Inline Functions

```kotlin
// 1. Higher-order functions that would otherwise allocate lambda objects
//    (hot path in request handling)
inline fun <T> withTransaction(crossinline block: () -> T): T {
    return transaction { block() }
}

// 2. Reified type parameters (serialization helpers)
inline fun <reified T> ApplicationCall.receiveValidated(): T {
    val body = receive<T>()
    // validation logic
    return body
}

// 3. Measured blocks (logging/metrics)
inline fun <T> measured(label: String, block: () -> T): T {
    val start = System.nanoTime()
    return try {
        block()
    } finally {
        val ms = (System.nanoTime() - start) / 1_000_000.0
        logger.debug { "$label took ${ms}ms" }
    }
}

// DON'T inline: large function bodies (increases bytecode), or functions
// that capture mutable state across call sites.
```

---

## 3. Security

### 3.1 JWT Dual-Domain Isolation

Matching the Go implementation's architecture — customer and admin tokens use separate secrets:

```kotlin
data class JwtConfig(
    val customerSecret: String,
    val customerAccessTtl: Duration,
    val customerRefreshTtl: Duration,
    val adminSecret: String,
    val adminAccessTtl: Duration,
    val adminRefreshTtl: Duration,
    val currentKid: String,        // key ID for rotation
)

class JwtService(private val cfg: JwtConfig) {

    // Customer tokens: iss=gabon-service, aud=customer
    fun generateCustomerTokens(customerId: Long): TokenPair =
        generatePair(customerId, role = null, issuer = "gabon-service", audience = "customer",
            secret = cfg.customerSecret, accessTtl = cfg.customerAccessTtl,
            refreshTtl = cfg.customerRefreshTtl)

    // Admin tokens: iss=gabon-admin, aud=admin
    fun generateAdminTokens(adminId: Long, role: String): TokenPair =
        generatePair(adminId, role = role, issuer = "gabon-admin", audience = "admin",
            secret = cfg.adminSecret, accessTtl = cfg.adminAccessTtl,
            refreshTtl = cfg.adminRefreshTtl)

    private fun generatePair(
        userId: Long, role: String?, issuer: String, audience: String,
        secret: String, accessTtl: Duration, refreshTtl: Duration,
    ): TokenPair {
        val now = Instant.now()
        val familyId = UUID.randomUUID().toString()

        val accessToken = buildJwt(userId, role, familyId, "access", issuer, audience,
            secret, now, now.plus(accessTtl))
        val refreshToken = buildJwt(userId, role, familyId, "refresh", issuer, audience,
            secret, now, now.plus(refreshTtl))

        return TokenPair(accessToken, refreshToken, familyId,
            now.plus(accessTtl), now.plus(refreshTtl))
    }

    private fun buildJwt(
        userId: Long, role: String?, familyId: String, tokenType: String,
        issuer: String, audience: String, secret: String,
        issuedAt: Instant, expiresAt: Instant,
    ): String = JWT.create()
        .withKeyId(cfg.currentKid)       // KID header for key rotation
        .withIssuer(issuer)
        .withAudience(audience)
        .withSubject(userId.toString())
        .withJWTId(UUID.randomUUID().toString())
        .withClaim("token_type", tokenType)
        .withClaim("family_id", familyId)
        .apply { role?.let { withClaim("role", it) } }
        .withIssuedAt(issuedAt)
        .withExpiresAt(expiresAt)
        .sign(Algorithm.HMAC256(secret))

    // Parse with triple validation: algorithm + issuer + audience
    fun parseCustomerToken(token: String): TokenClaims =
        parseToken(token, cfg.customerSecret, "gabon-service", "customer")

    fun parseAdminToken(token: String): TokenClaims =
        parseToken(token, cfg.adminSecret, "gabon-admin", "admin")

    private fun parseToken(
        token: String, secret: String,
        expectedIssuer: String, expectedAudience: String,
    ): TokenClaims {
        val verifier = JWT.require(Algorithm.HMAC256(secret))
            .withIssuer(expectedIssuer)
            .withAudience(expectedAudience)
            .build()

        val decoded = verifier.verify(token) // throws JWTVerificationException
        return TokenClaims(
            userId = decoded.subject.toLong(),
            jti = decoded.id,
            familyId = decoded.getClaim("family_id").asString(),
            tokenType = decoded.getClaim("token_type").asString(),
            role = decoded.getClaim("role")?.asString(),
            expiresAt = decoded.expiresAtAsInstant,
        )
    }
}
```

**Ktor auth plugin configuration:**

```kotlin
fun Application.configureAuth(jwtService: JwtService, cfg: JwtConfig) {
    install(Authentication) {
        jwt("customer-jwt") {
            verifier(JWT.require(Algorithm.HMAC256(cfg.customerSecret))
                .withIssuer("gabon-service")
                .withAudience("customer")
                .build())
            validate { credential ->
                val tokenType = credential.payload.getClaim("token_type")?.asString()
                if (tokenType == "access") JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized,
                    ApiResponse.error("AUTH_TOKEN_INVALID", "invalid or expired token"))
            }
        }
        jwt("admin-jwt") {
            verifier(JWT.require(Algorithm.HMAC256(cfg.adminSecret))
                .withIssuer("gabon-admin")
                .withAudience("admin")
                .build())
            validate { credential ->
                val tokenType = credential.payload.getClaim("token_type")?.asString()
                if (tokenType == "access") JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized,
                    ApiResponse.error("AUTH_TOKEN_INVALID", "invalid or expired token"))
            }
        }
    }
}
```

### 3.2 Password Hashing

```kotlin
import at.favre.lib.crypto.bcrypt.BCrypt

object PasswordHasher {
    // Cost factor 12: ~250ms per hash on modern hardware.
    // Cost 10 is fine for benchmarks; 12 for production.
    private const val COST = 12

    fun hash(password: String): String =
        BCrypt.withDefaults().hashToString(COST, password.toCharArray())

    fun verify(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified
}
```

### 3.3 Rate Limiting with Redis Sliding Window

Port the Go implementation's sorted-set sliding window algorithm:

```kotlin
import io.lettuce.core.api.sync.RedisCommands
import java.time.Duration
import java.time.Instant

class RateLimiter(
    private val redis: RedisCommands<String, String>,
    private val group: String,       // "auth", "api", "admin"
    private val limit: Int,          // max requests per window
    private val window: Duration,    // sliding window size
) {
    data class Result(val allowed: Boolean, val remaining: Int, val retryAfterSeconds: Int?)

    fun check(identifier: String): Result {
        val key = "rl:$group:$identifier"
        val now = Instant.now()
        val nowMicro = now.toEpochMilli() * 1000
        val windowStart = now.minus(window).toEpochMilli() * 1000

        // Atomic pipeline: remove expired + add current + count + set TTL
        val member = "$nowMicro:${java.util.UUID.randomUUID().toString().take(8)}"

        // Use Lettuce multi/exec for atomicity
        redis.multi()
        redis.zremrangebyscore(key, "-inf", windowStart.toString())
        redis.zadd(key, nowMicro.toDouble(), member)
        redis.zcard(key)
        redis.expire(key, window.seconds + 1)
        val results = redis.exec()

        val count = (results[2] as Long).toInt()
        val remaining = (limit - count).coerceAtLeast(0)

        return if (count > limit) {
            Result(allowed = false, remaining = 0, retryAfterSeconds = window.seconds.toInt())
        } else {
            Result(allowed = true, remaining = remaining, retryAfterSeconds = null)
        }
    }
}

// Ktor plugin wrapping the rate limiter
fun Route.rateLimited(
    rateLimiter: RateLimiter,
    keyExtractor: (ApplicationCall) -> String,
    block: Route.() -> Unit,
) {
    install(createRouteScopedPlugin("RateLimit") {
        onCall { call ->
            val key = keyExtractor(call)
            val result = rateLimiter.check(key)

            call.response.header("X-RateLimit-Limit", rateLimiter.toString())
            call.response.header("X-RateLimit-Remaining", result.remaining.toString())

            if (!result.allowed) {
                call.response.header("Retry-After", result.retryAfterSeconds.toString())
                call.respond(HttpStatusCode.TooManyRequests,
                    ApiResponse.error("RATE_LIMITED", "too many requests, please try again later"))
                return@onCall
            }
        }
    })
    block()
}
```

### 3.4 Input Validation

```kotlin
// Validation as a standalone function — not coupled to any framework
@Serializable
data class CreateVideoRequest(
    val title: String,
    @SerialName("video_url") val videoUrl: String,
    @SerialName("cover_url") val coverUrl: String? = null,
)

fun CreateVideoRequest.validate(): List<String> = buildList {
    if (title.isBlank()) add("title is required")
    if (title.length > 200) add("title must be 200 characters or fewer")
    if (!videoUrl.startsWith("https://")) add("video_url must use HTTPS")
    if (coverUrl != null && !coverUrl.startsWith("https://")) add("cover_url must use HTTPS")
}

// Reusable extension on ApplicationCall
suspend inline fun <reified T : Any> ApplicationCall.receiveValidated(
    validate: T.() -> List<String>,
): T {
    val body = receive<T>()
    val errors = body.validate()
    if (errors.isNotEmpty()) {
        throw AppError.BadRequest(errors.joinToString("; "))
    }
    return body
}

// Usage in handler:
post("/api/videos") {
    val req = call.receiveValidated<CreateVideoRequest> { validate() }
    // req is guaranteed valid here
}
```

### 3.5 SQL Injection Prevention with Exposed

Exposed DSL is safe by default — all `.where {}` expressions are parameterized:

```kotlin
// SAFE — Exposed parameterizes automatically
Videos.selectAll().where { Videos.title like "%$query%" }
// Generated: SELECT ... WHERE title LIKE ?  (with parameter binding)

// UNSAFE — raw string interpolation in exec()
// NEVER do this:
exec("SELECT * FROM videos WHERE title = '$userInput'")

// SAFE — parameterized raw SQL
exec(
    "SELECT * FROM videos WHERE title = ? AND status = ?",
    listOf(VarCharColumnType() to title, VarCharColumnType() to status),
) { rs -> /* ... */ }
```

### 3.6 CORS Configuration

```kotlin
install(CORS) {
    // Explicit origins — never use anyHost() in production
    allowHost("app.example.com", schemes = listOf("https"))
    allowHost("admin.example.com", schemes = listOf("https"))

    // Development only
    if (isDev) {
        allowHost("localhost:3000")
        allowHost("localhost:5173")
    }

    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.ContentType)
    allowMethod(HttpMethod.Put)
    allowMethod(HttpMethod.Delete)
    allowMethod(HttpMethod.Patch)

    // Expose rate limit headers to frontend
    exposeHeader("X-RateLimit-Limit")
    exposeHeader("X-RateLimit-Remaining")

    maxAgeInSeconds = 3600
}
```

---

## 4. Testing Strategy

### 4.1 Unit Testing Services with Mock Repositories

```kotlin
// Define repository interfaces for testability
interface CustomerRepository {
    fun findById(id: Long): Customer?
    fun findByUsername(username: String): Customer?
    fun create(username: String, passwordHash: String, nickname: String?): Customer
}

// Production implementation
class ExposedCustomerRepository : CustomerRepository {
    override fun findByUsername(username: String): Customer? = transaction {
        Customers.selectAll()
            .where { Customers.username.lowerCase() eq username.lowercase() }
            .where { Customers.deletedAt.isNull() }
            .singleOrNull()
            ?.toCustomer()
    }
    // ...
}

// Test with mock
class AuthServiceTest {
    private val mockRepo = mockk<CustomerRepository>()
    private val mockRedis = mockk<RedisCommands<String, String>>(relaxed = true)
    private val jwtService = JwtService(testJwtConfig())
    private val service = AuthService(mockRepo, jwtService, mockRedis)

    @Test
    fun `login with wrong password returns InvalidCredentials`() {
        val customer = Customer(id = 1, username = "alice",
            passwordHash = PasswordHasher.hash("correct"))
        every { mockRepo.findByUsername("alice") } returns customer

        val result = service.login("alice", "wrong")

        assertTrue(result.isFailure)
        assertIs<AppError.InvalidCredentials>(result.exceptionOrNull())
    }

    @Test
    fun `login success returns token pair`() {
        val password = "securepass"
        val customer = Customer(id = 1, username = "alice",
            passwordHash = PasswordHasher.hash(password))
        every { mockRepo.findByUsername("alice") } returns customer

        val result = service.login("alice", password)

        assertTrue(result.isSuccess)
        val pair = result.getOrThrow()
        assertNotNull(pair.accessToken)
        assertNotNull(pair.refreshToken)
    }
}
```

### 4.2 Integration Testing with Testcontainers

```kotlin
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
abstract class IntegrationTestBase {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:18-alpine").apply {
            withDatabaseName("gabon_test")
            withUsername("test")
            withPassword("test")
        }

        @Container
        val redis = GenericContainer("redis:8-alpine").apply {
            withExposedPorts(6379)
            withCommand("redis-server", "--requirepass", "testpass")
        }

        // Run migrations once before all tests
        @JvmStatic
        @BeforeAll
        fun migrate() {
            val config = HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                maximumPoolSize = 5
            }
            val ds = HikariDataSource(config)
            Database.connect(ds)

            transaction {
                // Execute migration SQL files
                SchemaUtils.create(Customers, Videos, VideoLikes /* ... */)
            }
        }
    }

    protected val testDataSource by lazy {
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            maximumPoolSize = 5
        })
    }

    protected val testRedis by lazy {
        RedisClient.create(
            "redis://:testpass@${redis.host}:${redis.firstMappedPort}"
        ).connect().sync()
    }

    @BeforeEach
    fun cleanDatabase() {
        transaction {
            // Truncate all tables between tests
            exec("TRUNCATE customers, videos, video_likes RESTART IDENTITY CASCADE")
        }
    }
}
```

### 4.3 Ktor testApplication Setup

```kotlin
class VideoRoutesTest : IntegrationTestBase() {

    private fun ApplicationTestBuilder.configureTestApp() {
        application {
            val deps = Dependencies(
                dataSource = testDataSource,
                redis = testRedis,
                jwtConfig = testJwtConfig(),
            )
            configureSerialization()
            configureAuth(deps.jwtService, deps.jwtConfig)
            configureRouting(deps)
        }
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json(AppJson) }
    }

    @Test
    fun `GET videos returns paginated list`() = testApplication {
        configureTestApp()
        val client = jsonClient()

        // Seed test data
        transaction {
            Customers.insert {
                it[username] = "alice"
                it[passwordHash] = PasswordHasher.hash("pass1234")
            }
            Videos.insert {
                it[title] = "Test Video"
                it[videoUrl] = "https://example.com/v.mp4"
                it[status] = VideoStatus.APPROVED
                it[customerId] = 1
                it[createdAt] = Instant.now()
            }
        }

        val response = client.get("/api/videos")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<ApiResponse<PageResponse<VideoResponse>>>()
        assertEquals("OK", body.code)
        assertEquals(1, body.data?.total)
    }

    @Test
    fun `POST videos requires authentication`() = testApplication {
        configureTestApp()
        val client = jsonClient()

        val response = client.post("/api/videos") {
            contentType(ContentType.Application.Json)
            setBody(CreateVideoRequest(title = "Test", videoUrl = "https://x.com/v.mp4"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST videos with valid token creates video`() = testApplication {
        configureTestApp()
        val client = jsonClient()

        // Create customer and get token
        val tokenPair = seedCustomerAndLogin("alice", "pass1234")

        val response = client.post("/api/videos") {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenPair.accessToken)
            setBody(CreateVideoRequest(title = "My Video", videoUrl = "https://x.com/v.mp4"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }
}
```

### 4.4 Concurrent Operation Testing

```kotlin
class ConcurrencyTest : IntegrationTestBase() {

    @Test
    fun `concurrent likes on same video increment count exactly once per user`() = runTest {
        val videoId = seedVideo()
        val customerId = seedCustomer("alice")
        val token = generateToken(customerId)

        // Fire 10 concurrent like requests — only the first should succeed
        val results = (1..10).map {
            async(Dispatchers.IO) {
                testClient.post("/api/videos/$videoId/like") {
                    bearerAuth(token)
                }
            }
        }.awaitAll()

        val successCount = results.count { it.status == HttpStatusCode.OK }
        val conflictCount = results.count { it.status == HttpStatusCode.Conflict }

        assertEquals(1, successCount, "exactly one like should succeed")
        assertEquals(9, conflictCount, "rest should be conflicts")

        // Verify counter integrity
        val video = transaction {
            Videos.selectAll().where { Videos.id eq videoId }.single()
        }
        assertEquals(1, video[Videos.likeCount], "like_count must be exactly 1")
    }

    @Test
    fun `concurrent refresh with same token — only one succeeds`() = runTest {
        val customerId = seedCustomer("alice")
        val tokenPair = authService.login("alice", "pass1234").getOrThrow()

        val results = (1..5).map {
            async(Dispatchers.IO) {
                testClient.post("/api/auth/refresh") {
                    bearerAuth(tokenPair.refreshToken)
                }
            }
        }.awaitAll()

        val successCount = results.count { it.status == HttpStatusCode.OK }
        assertEquals(1, successCount, "only one refresh should succeed")
    }

    @Test
    fun `concurrent task claims — only one awards diamonds`() = runTest {
        val taskId = seedDailyTask(reward = 100)
        val customers = (1..1).map { seedCustomer("user$it") }

        // Same user, concurrent claims
        val token = generateToken(customers[0])
        val results = (1..5).map {
            async(Dispatchers.IO) {
                testClient.post("/api/tasks/$taskId/claim") {
                    bearerAuth(token)
                }
            }
        }.awaitAll()

        val successCount = results.count { it.status == HttpStatusCode.OK }
        assertEquals(1, successCount, "only one claim should succeed")
    }
}
```

---

## 5. Observability

### 5.1 Structured Logging

```kotlin
import io.ktor.server.plugins.calllogging.*
import org.slf4j.event.Level

fun Application.configureLogging() {
    install(CallLogging) {
        level = Level.INFO

        // Structured format: method, path, status, duration
        format { call ->
            val status = call.response.status()
            val method = call.request.httpMethod.value
            val path = call.request.path()
            val duration = call.processingTimeMillis()
            val requestId = call.request.header("X-Request-Id") ?: "-"
            "$method $path -> $status (${duration}ms) [$requestId]"
        }

        // Don't log health checks — they pollute logs during benchmarks
        filter { call -> call.request.path() != "/health" }

        // Add MDC values for structured logging frameworks (logback)
        mdc("request_id") { call ->
            call.request.header("X-Request-Id") ?: java.util.UUID.randomUUID().toString()
        }
        mdc("remote_ip") { call ->
            call.request.origin.remoteAddress
        }
    }
}
```

**logback.xml for JSON structured output:**

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>request_id</includeMdcKeyName>
            <includeMdcKeyName>remote_ip</includeMdcKeyName>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
    <!-- Silence HikariCP and Exposed at INFO -->
    <logger name="com.zaxxer.hikari" level="WARN"/>
    <logger name="Exposed" level="WARN"/>
</configuration>
```

### 5.2 OpenTelemetry Integration

```kotlin
fun Application.configureOpenTelemetry() {
    // Option 1: Auto-instrumentation via Ktor plugin (recommended)
    install(io.ktor.server.plugins.opentelemetry.OpenTelemetry) {
        // Uses OTEL_EXPORTER_OTLP_ENDPOINT env var automatically
    }

    // Option 2: Programmatic configuration
    val spanExporter = OtlpGrpcSpanExporter.builder()
        .setEndpoint("http://localhost:4317")
        .build()

    val tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
        .setResource(Resource.builder()
            .put("service.name", "gabon-kotlin")
            .put("service.version", "0.1.0")
            .build())
        .build()

    val openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .buildAndRegisterGlobal()

    // Custom spans for database operations
    val tracer = openTelemetry.getTracer("gabon-kotlin")

    // Wrap Exposed transactions with spans
    fun <T> tracedTransaction(name: String, block: Transaction.() -> T): T {
        val span = tracer.spanBuilder(name)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute("db.system", "postgresql")
            .startSpan()
        return try {
            span.makeCurrent().use {
                transaction { block() }
            }
        } catch (e: Exception) {
            span.setStatus(StatusCode.ERROR, e.message ?: "unknown")
            span.recordException(e)
            throw e
        } finally {
            span.end()
        }
    }
}
```

### 5.3 Health Check Endpoint

```kotlin
fun Route.healthRoutes() {
    // Liveness — always returns 200 (process is running)
    get("/health") {
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }

    // Readiness — checks database + Redis connectivity
    get("/health/ready") {
        val checks = buildMap {
            put("postgres", checkPostgres())
            put("redis", checkRedis())
        }
        val allHealthy = checks.values.all { it == "ok" }
        val status = if (allHealthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respond(status, mapOf("status" to if (allHealthy) "ok" else "degraded", "checks" to checks))
    }
}

private fun checkPostgres(): String = try {
    transaction { exec("SELECT 1") { it.next(); "ok" } ?: "no result" }
} catch (e: Exception) { "error: ${e.message}" }

private fun checkRedis(): String = try {
    val pong = redis.ping()
    if (pong == "PONG") "ok" else "unexpected: $pong"
} catch (e: Exception) { "error: ${e.message}" }
```

---

## 6. Build & Deployment

### 6.1 Gradle Kotlin DSL Optimization

```kotlin
// gradle.properties
org.gradle.configuration-cache=true
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.jvmargs=-Xmx1g -XX:+UseParallelGC
kotlin.incremental=true
kotlin.daemon.jvmargs=-Xmx1g

// build.gradle.kts
plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("io.ktor.plugin") version "3.4.0"
    id("com.google.devtools.ksp") version "2.3.20-1.0.32"  // if using KSP-based libs
}

application {
    mainClass.set("com.gabon.ApplicationKt")
}

ktor {
    fatJar {
        archiveFileName.set("gabon-kotlin.jar")
    }
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-rate-limit")
    implementation("io.ktor:ktor-serialization-kotlinx-json")

    // Database
    implementation("org.jetbrains.exposed:exposed-core:1.1.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.1.1")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:1.1.1")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.postgresql:postgresql:42.7.5")

    // Redis
    implementation("io.lettuce:lettuce-core:7.3.0")

    // Auth
    implementation("com.auth0:java-jwt:4.5.0")
    implementation("at.favre.lib:bcrypt:0.10.2")

    // S3
    implementation("aws.sdk.kotlin:s3:1.4.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.18")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-content-negotiation")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation("org.testcontainers:testcontainers:1.21.0")
    testImplementation("org.testcontainers:postgresql:1.21.0")
    testImplementation("org.testcontainers:junit-jupiter:1.21.0")
}
```

### 6.2 Docker Multi-Stage Build

```dockerfile
# syntax=docker/dockerfile:1
# --- Stage 1: Build ---
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Cache Gradle wrapper
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

# Build fat jar
COPY src/ src/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew buildFatJar --no-daemon -x test

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app

# Timezone data (matching Go/Rust: Asia/Shanghai for period key)
RUN apk add --no-cache tzdata

COPY --from=builder /build/build/libs/gabon-kotlin.jar /app/gabon-kotlin.jar

USER app
WORKDIR /app

ENV JAVA_OPTS="-server \
    -XX:+UseZGC \
    -XX:+ZGenerational \
    -Xms256m \
    -Xmx512m \
    -XX:+AlwaysPreTouch \
    -Djdk.virtualThreadScheduler.maxPoolSize=256"

EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8081/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar gabon-kotlin.jar"]
```

**Comparison of image sizes (estimated):**

| | Go | Rust | Java (Spring) | Kotlin (Ktor) |
|---|---|---|---|---|
| Base | scratch (0) | debian-slim (80MB) | zulu-jre (260MB) | temurin-jre-alpine (~120MB) |
| App | 44MB | 19MB | ~50MB fat jar | ~30MB fat jar |
| Total | **44MB** | **130MB** | **412MB** | **~150MB** |

### 6.3 GraalVM Native Image Considerations

For competing with Go/Rust on cold start and memory, GraalVM native-image is the path:

```kotlin
// Key constraints:
// 1. CIO engine instead of Netty (Netty has complex reflection needs)
// 2. kotlinx-serialization works out of the box (no reflection)
// 3. Exposed JDBC needs reflection config for PostgreSQL driver

// build.gradle.kts
plugins {
    id("org.graalvm.buildtools.native") version "0.10.6"
}

graalvmNative {
    binaries {
        named("main") {
            mainClass.set("com.gabon.ApplicationKt")
            buildArgs.addAll(
                "--no-fallback",
                "--enable-http",
                "--enable-https",
                "-O2",                          // Optimization level
                "-march=native",                // Use host CPU features
                "-H:+ReportExceptionStackTraces",
            )
        }
    }
}
```

**reflect-config.json** (required for JDBC driver and Exposed):

```json
[
  {
    "name": "org.postgresql.Driver",
    "methods": [{ "name": "<init>", "parameterTypes": [] }]
  },
  {
    "name": "org.postgresql.PGProperty",
    "allDeclaredFields": true,
    "allDeclaredMethods": true
  }
]
```

**Expected native-image results:**

| Metric | JVM (ZGC) | Native Image |
|--------|-----------|--------------|
| Cold start | ~800ms (Ktor << Spring's 2.7s) | ~50ms |
| RSS idle | ~180MB | ~40MB |
| Peak QPS | Higher (JIT optimizes hot paths) | ~15-20% lower |

Trade-off: native-image gives Go-like startup and memory at the cost of peak throughput and build time (~3min).

### 6.4 Fat Jar vs Layered Jar

For a benchmarking project, **fat jar is the right choice**:

```kotlin
// Fat jar: single file, simple Docker COPY, simple ENTRYPOINT
// Ktor's buildFatJar task handles this
tasks.named<Jar>("buildFatJar") {
    // Exclude unnecessary files to reduce size
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}
```

Layered jars (Spring Boot style) make sense only when:
- Docker layer caching matters (CI/CD frequent deploys where dependencies rarely change)
- Image pull speed is critical (shared base layers)

For this benchmark, fat jar keeps things simple and comparable to Go's single binary.

---

## 7. Code Quality

### 7.1 Static Analysis with Detekt

```yaml
# detekt.yml
complexity:
  LongMethod:
    threshold: 40        # Ktor handlers can be verbose
  TooManyFunctions:
    thresholdInFiles: 20
  CyclomaticComplexity:
    threshold: 10

style:
  ForbiddenComment:
    values: ["TODO", "FIXME", "HACK"]  # No TODOs in release code
  MaxLineLength:
    maxLineLength: 120
  WildcardImport:
    active: true         # Explicit imports only

exceptions:
  TooGenericExceptionCaught:
    active: true
    exceptionNames:
      - Exception         # Must catch specific types
    allowedExceptionNameRegex: "_"

potential-bugs:
  DoubleMutabilityForCollection:
    active: true          # Use val + mutable collection OR var + immutable, not both
```

```kotlin
// build.gradle.kts
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

detekt {
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
}
```

### 7.2 Exposed SQL Logging for Query Debugging

```kotlin
// Enable during development / debugging — disable for benchmarks
fun Application.configureDatabaseLogging() {
    if (isDev) {
        // Exposed built-in SQL logger — prints every query
        org.jetbrains.exposed.sql.addLogger(StdOutSqlLogger)

        // Or use SLF4J logger for structured output
        org.jetbrains.exposed.sql.addLogger(Slf4jSqlDebugLogger)
    }
}

// Custom logger that includes execution time
class TimedSqlLogger : SqlLogger {
    override fun log(context: StatementContext, transaction: Transaction) {
        val sql = context.expandArgs(transaction)
        val duration = context.duration
        if (duration > 100) {
            log.warn("SLOW QUERY (${duration}ms): $sql")
        } else {
            log.debug("SQL (${duration}ms): $sql")
        }
    }
}

// Usage: add per-transaction when investigating
transaction {
    addLogger(TimedSqlLogger())
    // ... queries here will be logged with timing
}
```

**Benchmark-mode: disable all logging:**

```kotlin
// In production/benchmark, set logback root to WARN
// and explicitly silence Exposed:
// logback.xml: <logger name="Exposed" level="OFF"/>
```

---

## Appendix: Quick Reference

### Project Structure

```
kotlin/
├── src/main/kotlin/com/gabon/
│   ├── Application.kt              # Entry point + module configuration
│   ├── config/
│   │   └── AppConfig.kt            # Environment variables → data class
│   ├── plugins/
│   │   ├── Serialization.kt        # ContentNegotiation + Json config
│   │   ├── Authentication.kt       # JWT dual-domain setup
│   │   ├── Routing.kt              # Compose all route extensions
│   │   ├── StatusPages.kt          # Error → HTTP mapping
│   │   └── Monitoring.kt           # Logging + OpenTelemetry
│   ├── routes/
│   │   ├── AuthRoutes.kt           # Route.authRoutes(service)
│   │   ├── VideoRoutes.kt
│   │   ├── AdminRoutes.kt
│   │   └── HealthRoutes.kt
│   ├── service/
│   │   ├── AuthService.kt
│   │   ├── VideoService.kt
│   │   ├── JwtService.kt
│   │   └── AdminService.kt
│   ├── repository/
│   │   ├── CustomerRepository.kt   # Interface + ExposedCustomerRepository
│   │   ├── VideoRepository.kt
│   │   └── Tables.kt               # All Exposed table definitions
│   ├── model/
│   │   ├── AppError.kt             # Sealed class error hierarchy
│   │   ├── DTOs.kt                 # @Serializable request/response
│   │   └── ApiResponse.kt          # Unified response wrapper
│   └── infra/
│       ├── Database.kt             # HikariCP + Exposed setup
│       ├── Redis.kt                # Lettuce connection
│       └── S3Storage.kt            # AWS SDK Kotlin
├── src/main/resources/
│   ├── logback.xml
│   └── db/migration/               # SQL migrations (Flyway or manual)
├── src/test/kotlin/com/gabon/
│   ├── service/                    # Unit tests with MockK
│   ├── routes/                     # Ktor testApplication tests
│   └── integration/                # Testcontainers tests
├── build.gradle.kts
├── gradle.properties
├── Dockerfile
└── Makefile
```

### Makefile

```makefile
.PHONY: dev build test lint docker-build docker-run

dev:
	./gradlew run

build:
	./gradlew buildFatJar

test:
	./gradlew test

lint:
	./gradlew detekt

docker-build:
	docker build -t gabon-kotlin .

docker-run:
	docker run --rm --env-file ../.env -p 8081:8081 gabon-kotlin
```

### Environment Variables

```bash
# Added to .env.example for Kotlin implementation
KOTLIN_PORT=8081
```

### Service Port

| Service | Port | API Prefix |
|---------|------|-----------|
| Go | 8080 | `/api/v1/`, `/admin/v1/` |
| Rust | 3000 | `/api/`, `/admin/` |
| Java | 8082 | `/service/api/` |
| **Kotlin** | **8081** | **`/api/`, `/admin/`** |
