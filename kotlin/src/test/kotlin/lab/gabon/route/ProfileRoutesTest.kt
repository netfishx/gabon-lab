package lab.gabon.route

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import lab.gabon.config.JwtConfig
import lab.gabon.config.S3Config
import lab.gabon.plugin.PreserveAwareSnakeCase
import lab.gabon.plugin.configureAuthentication
import lab.gabon.plugin.configureErrorHandling
import lab.gabon.plugin.configureRouting
import lab.gabon.plugin.configureSerialization
import lab.gabon.repository.CustomerRepo
import lab.gabon.repository.CustomerRow
import lab.gabon.repository.SocialRepo
import lab.gabon.service.AuthService
import lab.gabon.service.JwtService
import lab.gabon.service.RedisTokenStore
import lab.gabon.service.SocialService
import lab.gabon.service.StorageService
import lab.gabon.service.UserService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ProfileRoutesTest {
    private val jwtConfig =
        JwtConfig(
            customerSecret = "test-customer-secret-at-least-32-chars-long",
            adminSecret = "test-admin-secret-at-least-32-chars-long-too",
            customerAccessTtl = 15.minutes,
            customerRefreshTtl = 168.hours,
            adminAccessTtl = 15.minutes,
            adminRefreshTtl = 168.hours,
            currentKid = "kid-test-001",
        )

    private val s3Config =
        S3Config(
            endpoint = "",
            region = "us-east-1",
            accessKey = "test-key",
            secretKey = "test-secret",
            bucketVideos = "gabon-videos",
            bucketAvatars = "gabon-avatars",
        )

    private val jwtService = JwtService(jwtConfig)
    private lateinit var customerRepo: CustomerRepo
    private lateinit var tokenStore: RedisTokenStore
    private lateinit var userService: UserService
    private lateinit var storageService: StorageService

    private val now = Clock.System.now()

    private fun customerRow(
        id: Long = 1L,
        username: String = "alice",
        name: String? = "Alice",
        phone: String? = "13800138000",
        email: String? = "alice@test.com",
        avatarUrl: String? = null,
        signature: String? = "hello world",
        diamondBalance: Long = 100,
    ) = CustomerRow(
        id = id,
        username = username,
        passwordHash = "hash",
        name = name,
        phone = phone,
        email = email,
        avatarUrl = avatarUrl,
        signature = signature,
        isVip = false,
        diamondBalance = diamondBalance,
        lastLoginAt = now,
        createdAt = now,
        updatedAt = now,
    )

    @OptIn(ExperimentalSerializationApi::class)
    private fun ApplicationTestBuilder.setupApp() {
        customerRepo = mockk()
        tokenStore = mockk()
        storageService = StorageService(s3Config)
        userService = UserService(customerRepo, storageService)

        val authService = mockk<AuthService>()
        val socialRepo = mockk<SocialRepo>()
        val socialService = SocialService(socialRepo, customerRepo)

        application {
            configureSerialization()
            configureErrorHandling()
            configureAuthentication(jwtService, tokenStore)
            configureRouting(authService, socialService, customerRepo, userService = userService)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun ApplicationTestBuilder.jsonClient() =
        createClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        namingStrategy = PreserveAwareSnakeCase
                        ignoreUnknownKeys = true
                    },
                )
            }
        }

    private fun aliceToken(): String {
        val tokenPair = jwtService.generateCustomerTokens(1L)
        return tokenPair.accessToken
    }

    // =====================================================
    // GET /users/me/profile
    // =====================================================

    @Test
    fun `get my profile - 200 with all private fields`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { customerRepo.findById(1L) } returns customerRow()

            val client = jsonClient()
            val response =
                client.get("/api/v1/users/me/profile") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val data = response.body<JsonObject>()["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(1, data["id"]?.jsonPrimitive?.int)
            assertEquals("alice", data["username"]?.jsonPrimitive?.content)
            assertEquals("Alice", data["name"]?.jsonPrimitive?.content)
            assertEquals("13800138000", data["phone"]?.jsonPrimitive?.content)
            assertEquals("alice@test.com", data["email"]?.jsonPrimitive?.content)
            assertEquals("hello world", data["signature"]?.jsonPrimitive?.content)
            assertEquals(false, data["is_vip"]?.jsonPrimitive?.boolean)
            assertEquals(100, data["diamond_balance"]?.jsonPrimitive?.long)
            assertNotNull(data["last_login_at"])
            assertNotNull(data["created_at"])
        }

    @Test
    fun `get my profile - 401 without auth`() =
        testApplication {
            setupApp()

            val client = jsonClient()
            val response = client.get("/api/v1/users/me/profile")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    // =====================================================
    // PUT /users/me/profile
    // =====================================================

    @Test
    fun `update profile - 200 partial update`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            val updated = customerRow(name = "NewAlice", signature = "new sig")
            coEvery { customerRepo.updateProfile(1L, "NewAlice", null, null, "new sig") } returns updated

            val client = jsonClient()
            val response =
                client.put("/api/v1/users/me/profile") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(aliceToken())
                    setBody(UpdateProfileRequest(name = "NewAlice", signature = "new sig"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val data = response.body<JsonObject>()["data"]?.jsonObject
            assertNotNull(data)
            assertEquals("NewAlice", data["name"]?.jsonPrimitive?.content)
        }

    @Test
    fun `update profile - empty fields do not overwrite`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            // Even with empty strings, the COALESCE NULLIF pattern keeps existing values
            val existing = customerRow()
            coEvery { customerRepo.updateProfile(1L, "", null, null, null) } returns existing

            val client = jsonClient()
            val response =
                client.put("/api/v1/users/me/profile") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(aliceToken())
                    setBody(UpdateProfileRequest(name = ""))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val data = response.body<JsonObject>()["data"]?.jsonObject
            assertNotNull(data)
            // Name should remain "Alice" because empty string is coalesced
            assertEquals("Alice", data["name"]?.jsonPrimitive?.content)
        }

    // =====================================================
    // POST /users/me/avatar/upload-url
    // =====================================================

    @Test
    fun `presign avatar upload - 200 with upload and avatar URLs`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            val client = jsonClient()
            val response =
                client.post("/api/v1/users/me/avatar/upload-url") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(aliceToken())
                    setBody(AvatarPresignRequest(fileName = "photo.jpg", contentType = "image/jpeg"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val data = response.body<JsonObject>()["data"]?.jsonObject
            assertNotNull(data)
            val uploadUrl = data["uploadUrl"]?.jsonPrimitive?.content
            val avatarUrl = data["avatarUrl"]?.jsonPrimitive?.content
            assertNotNull(uploadUrl)
            assertNotNull(avatarUrl)
            // Avatar URL should contain the avatars prefix
            assertTrue(avatarUrl.contains("avatars/1/"), "avatarUrl should contain avatars/{customerId}/: $avatarUrl")
            assertTrue(avatarUrl.endsWith(".jpg"), "avatarUrl should end with .jpg: $avatarUrl")
        }

    // =====================================================
    // POST /users/me/avatar/confirm
    // =====================================================

    @Test
    fun `confirm avatar upload - 200`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { customerRepo.updateAvatarUrl(1L, any()) } just Runs

            val client = jsonClient()
            val response =
                client.post("/api/v1/users/me/avatar/confirm") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(aliceToken())
                    setBody(AvatarConfirmRequest(avatarUrl = "https://cdn.example.com/avatars/1/abc.jpg"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<JsonObject>()
            assertEquals(0, body["code"]?.jsonPrimitive?.int)

            coVerify { customerRepo.updateAvatarUrl(1L, "https://cdn.example.com/avatars/1/abc.jpg") }
        }

    @Test
    fun `confirm avatar upload - 401 without auth`() =
        testApplication {
            setupApp()

            val client = jsonClient()
            val response =
                client.post("/api/v1/users/me/avatar/confirm") {
                    contentType(ContentType.Application.Json)
                    setBody(AvatarConfirmRequest(avatarUrl = "https://cdn.example.com/avatars/1/abc.jpg"))
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
}
