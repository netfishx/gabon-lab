package lab.gabon.plugin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import lab.gabon.model.JsonData

/** How to extract the rate limit identifier from the request. */
enum class KeyType {
    /** Use the client IP address. */
    IP,

    /** Use the authenticated customer ID (from JWT). */
    CUSTOMER_ID,

    /** Use the authenticated admin ID (from JWT). */
    ADMIN_ID,
}

/** Configuration for a single rate limit group. */
data class RateLimitConfig(
    val group: String,
    val limit: Int,
    val windowSeconds: Long = 60,
    val keyType: KeyType,
)

/**
 * Redis ZSET sliding window rate limiter.
 *
 * Uses a Lua script (not pipeline) to guarantee atomicity under concurrent requests.
 * Algorithm: ZREMRANGEBYSCORE + ZADD + ZCARD + EXPIRE in a single EVAL.
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RateLimiter(
    private val redis: RedisCoroutinesCommands<String, String>,
) {
    /**
     * Check and record a request against the rate limit.
     *
     * @return the current request count within the window (after adding this request)
     */
    suspend fun check(key: String, windowSeconds: Long): Long {
        val nowMicros = System.currentTimeMillis() * 1000 + (System.nanoTime() % 1000)
        val windowStart = nowMicros - windowSeconds * 1_000_000L
        val ttl = windowSeconds + 1

        val result = redis.eval<Long>(
            SLIDING_WINDOW_SCRIPT,
            ScriptOutputType.INTEGER,
            arrayOf(key),
            windowStart.toString(),
            nowMicros.toString(),
            ttl.toString(),
        )
        return result ?: 0L
    }

    private companion object {
        /**
         * Lua script for atomic sliding window rate limiting.
         *
         * KEYS[1] = rate limit key (e.g. rl:auth:10.0.0.1)
         * ARGV[1] = window start timestamp (now_micros - window)
         * ARGV[2] = current timestamp (now_micros, used as both score and member)
         * ARGV[3] = key TTL in seconds
         *
         * Returns: the count of entries in the ZSET after cleanup + add
         */
        val SLIDING_WINDOW_SCRIPT = """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[2])
            local count = redis.call('ZCARD', KEYS[1])
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return count
        """.trimIndent()
    }
}

/**
 * Create a Ktor route-scoped plugin that enforces rate limiting.
 *
 * Usage in routing:
 * ```
 * route("/api/v1") {
 *     rateLimit(rateLimiter, RateLimitConfig("auth", 20, keyType = KeyType.IP)) {
 *         authRoutes(...)
 *     }
 * }
 * ```
 */
fun Route.rateLimit(
    rateLimiter: RateLimiter,
    config: RateLimitConfig,
    build: Route.() -> Unit,
): Route {
    val rateLimitPlugin = createRouteScopedPlugin("RateLimit-${config.group}") {
        onCall { call ->
            val ip = call.request.local.remoteAddress
            val identifier = when (config.keyType) {
                KeyType.IP -> ip
                KeyType.CUSTOMER_ID -> {
                    val principal = call.principal<CustomerPrincipal>()
                    principal?.customerId?.toString() ?: ip
                }
                KeyType.ADMIN_ID -> {
                    val principal = call.principal<AdminPrincipal>()
                    principal?.adminId?.toString() ?: ip
                }
            }

            val key = "rl:${config.group}:$identifier"
            val count = rateLimiter.check(key, config.windowSeconds)
            val remaining = (config.limit - count).coerceAtLeast(0)

            call.response.header("X-RateLimit-Limit", config.limit.toString())
            call.response.header("X-RateLimit-Remaining", remaining.toString())

            if (count > config.limit) {
                call.response.header("Retry-After", config.windowSeconds.toString())
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    JsonData.error(429, "too many requests, please try again later"),
                )
            }
        }
    }

    val route = this.createChild(object : RouteSelector() {
        override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
            RouteSelectorEvaluation.Transparent
    })
    route.install(rateLimitPlugin)
    route.build()
    return route
}
