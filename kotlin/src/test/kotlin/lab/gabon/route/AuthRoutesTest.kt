package lab.gabon.route

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import lab.gabon.config.JwtConfig
import lab.gabon.model.JsonData
import lab.gabon.plugin.configureAuthentication
import lab.gabon.plugin.configureErrorHandling
import lab.gabon.plugin.configureRouting
import lab.gabon.plugin.configureSerialization
import lab.gabon.repository.CustomerRepo
import lab.gabon.repository.CustomerRow
import lab.gabon.service.AuthService
import lab.gabon.service.CasResult
import lab.gabon.service.JwtService
import lab.gabon.service.RedisTokenStore
import io.mockk.*
import kotlinx.datetime.Clock
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Integration tests for auth routes using Ktor testApplication.
 * Mocks CustomerRepo and RedisTokenStore (no real DB/Redis needed).
 * JwtService is real so we can verify actual token generation/parsing.
 */
class AuthRoutesTest {

    private val jwtConfig = JwtConfig(
        customerSecret = "test-customer-secret-at-least-32-chars-long",
        adminSecret = "test-admin-secret-at-least-32-chars-long-too",
        customerAccessTtl = 15.minutes,
        customerRefreshTtl = 168.hours,
        adminAccessTtl = 15.minutes,
        adminRefreshTtl = 168.hours,
        currentKid = "kid-test-001",
    )

    private val jwtService = JwtService(jwtConfig)
    private lateinit var customerRepo: CustomerRepo
    private lateinit var tokenStore: RedisTokenStore
    private lateinit var authService: AuthService

    // Bcrypt hash for "secret123" (pre-computed to avoid slow hashing in tests)
    // We'll use the real service which does its own hashing, but for mocking findByUsername
    // we need a real bcrypt hash.
    private val secret123Hash: String by lazy {
        at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(4, "secret123".toCharArray())
    }

    private val now = Clock.System.now()

    private fun aliceRow(id: Long = 1L, username: String = "alice") = CustomerRow(
        id = id,
        username = username,
        passwordHash = secret123Hash,
        name = "Alice",
        phone = "1234567890",
        email = "alice@test.com",
        avatarUrl = null,
        signature = null,
        isVip = false,
        diamondBalance = 100,
        lastLoginAt = null,
        createdAt = now,
        updatedAt = now,
    )

    @OptIn(ExperimentalSerializationApi::class)
    private fun ApplicationTestBuilder.setupApp() {
        customerRepo = mockk()
        tokenStore = mockk()
        authService = AuthService(customerRepo, jwtService, tokenStore)

        application {
            configureSerialization()
            configureErrorHandling()
            configureAuthentication(jwtService, tokenStore)
            configureRouting(authService)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) {
            json(Json {
                namingStrategy = JsonNamingStrategy.SnakeCase
                ignoreUnknownKeys = true
            })
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Register
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `register - success returns 201 with tokens`() = testApplication {
        setupApp()
        coEvery { customerRepo.findByUsername(any()) } returns null
        coEvery { customerRepo.create(any(), any()) } returns 1L
        coEvery { tokenStore.setFamily(any(), any(), any(), any()) } just Runs

        val client = jsonClient()
        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("alice", "secret123"))
        }

        assertEquals(HttpStatusCode.Created, response.status)

        val body = response.body<JsonObject>()
        assertEquals(0, body["code"]?.jsonPrimitive?.int)
        val data = body["data"]?.jsonObject
        assertNotNull(data)
        assertNotNull(data["access_token"]?.jsonPrimitive?.content)
        assertNotNull(data["refresh_token"]?.jsonPrimitive?.content)

        coVerify { customerRepo.create("alice", any()) }
        coVerify { tokenStore.setFamily(any(), 1L, any(), any()) }
    }

    @Test
    fun `register - duplicate username returns 409`() = testApplication {
        setupApp()
        coEvery { customerRepo.findByUsername("bob") } returns aliceRow(username = "bob")

        val client = jsonClient()
        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("bob", "secret123"))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)

        val body = response.body<JsonObject>()
        assertEquals("username already exists", body["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun `register - username too short returns 400`() = testApplication {
        setupApp()

        val client = jsonClient()
        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("ab", "secret123"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val body = response.body<JsonObject>()
        assertTrue(body["message"]?.jsonPrimitive?.content?.contains("3") == true)
    }

    @Test
    fun `register - password too short returns 400`() = testApplication {
        setupApp()

        val client = jsonClient()
        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("validuser", "12345"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val body = response.body<JsonObject>()
        assertTrue(body["message"]?.jsonPrimitive?.content?.contains("6") == true)
    }

    // ═══════════════════════════════════════════════════════════
    // Login
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `login - success returns 200 with tokens`() = testApplication {
        setupApp()
        coEvery { customerRepo.findByUsername("alice") } returns aliceRow()
        coEvery { customerRepo.updateLastLogin(1L) } just Runs
        coEvery { tokenStore.setFamily(any(), any(), any(), any()) } just Runs

        val client = jsonClient()
        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("alice", "secret123"))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<JsonObject>()
        assertEquals(0, body["code"]?.jsonPrimitive?.int)
        val data = body["data"]?.jsonObject
        assertNotNull(data)
        assertNotNull(data["access_token"]?.jsonPrimitive?.content)
        assertNotNull(data["refresh_token"]?.jsonPrimitive?.content)

        coVerify { customerRepo.updateLastLogin(1L) }
    }

    @Test
    fun `login - wrong password returns 401`() = testApplication {
        setupApp()
        coEvery { customerRepo.findByUsername("alice") } returns aliceRow()

        val client = jsonClient()
        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("alice", "wrongpass"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val body = response.body<JsonObject>()
        assertEquals("invalid username or password", body["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun `login - nonexistent user returns 401`() = testApplication {
        setupApp()
        coEvery { customerRepo.findByUsername("nonexistent") } returns null

        val client = jsonClient()
        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("nonexistent", "secret123"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ═══════════════════════════════════════════════════════════
    // Get /me
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `me - with valid token returns 200 with profile`() = testApplication {
        setupApp()
        coEvery { tokenStore.isBlacklisted(any()) } returns false
        coEvery { customerRepo.findById(1L) } returns aliceRow()

        // Generate a real token for customer 1
        val tokenPair = jwtService.generateCustomerTokens(1L)

        val client = jsonClient()
        val response = client.get("/api/v1/auth/me") {
            bearerAuth(tokenPair.accessToken)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<JsonObject>()
        val data = body["data"]?.jsonObject
        assertNotNull(data)
        assertEquals(1, data["id"]?.jsonPrimitive?.int)
        assertEquals("alice", data["username"]?.jsonPrimitive?.content)
        assertEquals("Alice", data["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `me - without token returns 401`() = testApplication {
        setupApp()

        val client = jsonClient()
        val response = client.get("/api/v1/auth/me")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `me - with blacklisted token returns 401`() = testApplication {
        setupApp()
        coEvery { tokenStore.isBlacklisted(any()) } returns true

        val tokenPair = jwtService.generateCustomerTokens(1L)

        val client = jsonClient()
        val response = client.get("/api/v1/auth/me") {
            bearerAuth(tokenPair.accessToken)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ═══════════════════════════════════════════════════════════
    // Change Password
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `change password - success returns 200`() = testApplication {
        setupApp()
        coEvery { tokenStore.isBlacklisted(any()) } returns false
        coEvery { customerRepo.findById(1L) } returns aliceRow()
        coEvery { customerRepo.updatePassword(1L, any()) } just Runs

        val tokenPair = jwtService.generateCustomerTokens(1L)

        val client = jsonClient()
        val response = client.put("/api/v1/auth/password") {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenPair.accessToken)
            setBody(ChangePasswordRequest("secret123", "newsecret456"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        coVerify { customerRepo.updatePassword(1L, any()) }
    }

    @Test
    fun `change password - wrong old password returns 400`() = testApplication {
        setupApp()
        coEvery { tokenStore.isBlacklisted(any()) } returns false
        coEvery { customerRepo.findById(1L) } returns aliceRow()

        val tokenPair = jwtService.generateCustomerTokens(1L)

        val client = jsonClient()
        val response = client.put("/api/v1/auth/password") {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenPair.accessToken)
            setBody(ChangePasswordRequest("wrongoldpass", "newsecret456"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val body = response.body<JsonObject>()
        assertEquals("password mismatch", body["message"]?.jsonPrimitive?.content)
    }

    // ═══════════════════════════════════════════════════════════
    // Logout
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `logout - success blacklists JTI and deletes family`() = testApplication {
        setupApp()
        coEvery { tokenStore.isBlacklisted(any()) } returns false
        coEvery { tokenStore.setBlacklist(any(), any()) } just Runs
        coEvery { tokenStore.deleteFamily(any()) } just Runs

        val tokenPair = jwtService.generateCustomerTokens(1L)

        val client = jsonClient()
        val response = client.post("/api/v1/auth/logout") {
            bearerAuth(tokenPair.accessToken)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        // Verify the JTI was blacklisted
        coVerify { tokenStore.setBlacklist(tokenPair.accessJti, any()) }
        // Verify the family was deleted
        coVerify { tokenStore.deleteFamily(tokenPair.familyId) }
    }

    // ═══════════════════════════════════════════════════════════
    // Refresh
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `refresh - success returns new tokens`() = testApplication {
        setupApp()

        val tokenPair = jwtService.generateCustomerTokens(1L)

        // Mock CAS to succeed
        coEvery {
            tokenStore.casFamily(tokenPair.familyId, tokenPair.refreshJti, any())
        } returns CasResult.Success(userId = 1L)

        val client = jsonClient()
        val response = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(tokenPair.refreshToken))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<JsonObject>()
        val data = body["data"]?.jsonObject
        assertNotNull(data)
        assertNotNull(data["access_token"]?.jsonPrimitive?.content)
        assertNotNull(data["refresh_token"]?.jsonPrimitive?.content)

        // New tokens should be different from old ones
        assertNotEquals(tokenPair.accessToken, data["access_token"]?.jsonPrimitive?.content)
        assertNotEquals(tokenPair.refreshToken, data["refresh_token"]?.jsonPrimitive?.content)
    }

    @Test
    fun `refresh - with access token returns 401`() = testApplication {
        setupApp()

        val tokenPair = jwtService.generateCustomerTokens(1L)

        val client = jsonClient()
        // Send the ACCESS token as if it were a refresh token
        val response = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(tokenPair.accessToken))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val body = response.body<JsonObject>()
        assertEquals("not a refresh token", body["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun `refresh - with expired family returns 401`() = testApplication {
        setupApp()

        val tokenPair = jwtService.generateCustomerTokens(1L)

        coEvery {
            tokenStore.casFamily(tokenPair.familyId, tokenPair.refreshJti, any())
        } returns CasResult.Missing

        val client = jsonClient()
        val response = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(tokenPair.refreshToken))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val body = response.body<JsonObject>()
        assertEquals("token family expired or revoked", body["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun `refresh - replay detected revokes family and returns 401`() = testApplication {
        setupApp()

        val tokenPair = jwtService.generateCustomerTokens(1L)

        coEvery {
            tokenStore.casFamily(tokenPair.familyId, tokenPair.refreshJti, any())
        } returns CasResult.Conflict

        val client = jsonClient()
        val response = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(tokenPair.refreshToken))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val body = response.body<JsonObject>()
        assertEquals("token reuse detected, family revoked", body["message"]?.jsonPrimitive?.content)
    }

    // ═══════════════════════════════════════════════════════════
    // Cross-Domain Token Rejection
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `me - admin token rejected on customer endpoint returns 401`() = testApplication {
        setupApp()

        // Generate an ADMIN token, not a customer token
        val adminTokenPair = jwtService.generateAdminTokens(1L, "superadmin")

        val client = jsonClient()
        val response = client.get("/api/v1/auth/me") {
            bearerAuth(adminTokenPair.accessToken)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ═══════════════════════════════════════════════════════════
    // Health check (sanity test)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `health endpoint returns 200`() = testApplication {
        setupApp()

        val client = jsonClient()
        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
    }
}
