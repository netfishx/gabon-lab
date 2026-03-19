package lab.gabon.plugin

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.mockk.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import lab.gabon.model.JsonData
import kotlin.test.*

/**
 * Unit tests for the sliding window rate limiter plugin.
 * Mocks RedisCoroutinesCommands to avoid needing a real Redis instance.
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalSerializationApi::class)
class RateLimitTest {

    private lateinit var redis: RedisCoroutinesCommands<String, String>
    private lateinit var rateLimiter: RateLimiter

    @BeforeTest
    fun setup() {
        redis = mockk()
        rateLimiter = RateLimiter(redis)
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) {
            json(Json {
                namingStrategy = JsonNamingStrategy.SnakeCase
                ignoreUnknownKeys = true
            })
        }
    }

    /**
     * Stub the Redis EVAL to return [count] for any call.
     */
    private fun stubRedisCount(count: Long) {
        coEvery {
            redis.eval<Long>(any<String>(), any<ScriptOutputType>(), any<Array<String>>(), any(), any(), any())
        } returns count
    }

    // ═══════════════════════════════════════════════════════════
    // Within limit: returns 200 with correct headers
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `request within limit returns 200 with rate limit headers`() = testApplication {
        // 1st request: count=1, limit=20 → remaining=19
        stubRedisCount(1L)

        application {
            configureSerialization()
            configureErrorHandling()
            routing {
                rateLimit(rateLimiter, RateLimitConfig("auth", 20, keyType = KeyType.IP)) {
                    get("/test") {
                        call.respond(HttpStatusCode.OK, JsonData.ok("hello"))
                    }
                }
            }
        }

        val client = jsonClient()
        val response = client.get("/test")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("20", response.headers["X-RateLimit-Limit"])
        assertEquals("19", response.headers["X-RateLimit-Remaining"])
        assertNull(response.headers["Retry-After"])
    }

    @Test
    fun `request at exact limit returns 200 with zero remaining`() = testApplication {
        // count=20, limit=20 → remaining=0 (at limit, but not exceeded)
        stubRedisCount(20L)

        application {
            configureSerialization()
            configureErrorHandling()
            routing {
                rateLimit(rateLimiter, RateLimitConfig("pub", 20, keyType = KeyType.IP)) {
                    get("/test") {
                        call.respond(HttpStatusCode.OK, JsonData.ok("hello"))
                    }
                }
            }
        }

        val client = jsonClient()
        val response = client.get("/test")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("20", response.headers["X-RateLimit-Limit"])
        assertEquals("0", response.headers["X-RateLimit-Remaining"])
        assertNull(response.headers["Retry-After"])
    }

    // ═══════════════════════════════════════════════════════════
    // Exceeds limit: returns 429 with Retry-After
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `request exceeding limit returns 429 with retry-after`() = testApplication {
        // count=21, limit=20 → blocked
        stubRedisCount(21L)

        application {
            configureSerialization()
            configureErrorHandling()
            routing {
                rateLimit(rateLimiter, RateLimitConfig("auth", 20, keyType = KeyType.IP)) {
                    get("/test") {
                        call.respond(HttpStatusCode.OK, JsonData.ok("hello"))
                    }
                }
            }
        }

        val client = jsonClient()
        val response = client.get("/test")

        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals("20", response.headers["X-RateLimit-Limit"])
        assertEquals("0", response.headers["X-RateLimit-Remaining"])
        assertEquals("60", response.headers["Retry-After"])

        val body = response.body<JsonObject>()
        assertEquals(429, body["code"]?.jsonPrimitive?.int)
        assertEquals(
            "too many requests, please try again later",
            body["message"]?.jsonPrimitive?.content,
        )
    }

    // ═══════════════════════════════════════════════════════════
    // Different groups have independent limits
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `different rate limit groups are independent`() = testApplication {
        // Track which keys are being used
        val capturedKeys = mutableListOf<String>()
        coEvery {
            redis.eval<Long>(any<String>(), any<ScriptOutputType>(), any<Array<String>>(), any(), any(), any())
        } coAnswers {
            val keys = thirdArg<Array<String>>()
            capturedKeys.add(keys[0])
            1L // always within limit
        }

        application {
            configureSerialization()
            configureErrorHandling()
            routing {
                rateLimit(rateLimiter, RateLimitConfig("auth", 20, keyType = KeyType.IP)) {
                    get("/auth-endpoint") {
                        call.respond(HttpStatusCode.OK, JsonData.ok("auth"))
                    }
                }
                rateLimit(rateLimiter, RateLimitConfig("pub", 120, keyType = KeyType.IP)) {
                    get("/pub-endpoint") {
                        call.respond(HttpStatusCode.OK, JsonData.ok("pub"))
                    }
                }
            }
        }

        val client = jsonClient()
        client.get("/auth-endpoint")
        client.get("/pub-endpoint")

        assertEquals(2, capturedKeys.size)
        assertTrue(capturedKeys[0].startsWith("rl:auth:"))
        assertTrue(capturedKeys[1].startsWith("rl:pub:"))
    }

    // ═══════════════════════════════════════════════════════════
    // Custom window config
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `custom window seconds reflected in retry-after`() = testApplication {
        stubRedisCount(11L) // exceeds limit of 10

        application {
            configureSerialization()
            configureErrorHandling()
            routing {
                rateLimit(rateLimiter, RateLimitConfig("custom", 10, windowSeconds = 120, keyType = KeyType.IP)) {
                    get("/test") {
                        call.respond(HttpStatusCode.OK, JsonData.ok("hello"))
                    }
                }
            }
        }

        val client = jsonClient()
        val response = client.get("/test")

        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals("120", response.headers["Retry-After"])
    }

    // ═══════════════════════════════════════════════════════════
    // Lua script is called with correct args
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `lua script receives correct key prefix`() = testApplication {
        val capturedKeys = mutableListOf<String>()
        coEvery {
            redis.eval<Long>(any<String>(), any<ScriptOutputType>(), any<Array<String>>(), any(), any(), any())
        } coAnswers {
            val keys = thirdArg<Array<String>>()
            capturedKeys.add(keys[0])
            5L
        }

        application {
            configureSerialization()
            configureErrorHandling()
            routing {
                rateLimit(rateLimiter, RateLimitConfig("auth", 20, windowSeconds = 60, keyType = KeyType.IP)) {
                    get("/test") {
                        call.respond(HttpStatusCode.OK, JsonData.ok("hello"))
                    }
                }
            }
        }

        val client = jsonClient()
        client.get("/test")

        assertEquals(1, capturedKeys.size)
        val key = capturedKeys[0]
        assertTrue(key.startsWith("rl:auth:"), "Key should start with rl:auth: but was $key")
    }
}
