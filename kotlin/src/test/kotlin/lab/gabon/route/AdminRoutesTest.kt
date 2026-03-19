package lab.gabon.route

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
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
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import lab.gabon.config.JwtConfig
import lab.gabon.model.AdminRole
import lab.gabon.model.AdminStatus
import lab.gabon.model.VideoStatus
import lab.gabon.plugin.PreserveAwareSnakeCase
import lab.gabon.plugin.configureAuthentication
import lab.gabon.plugin.configureErrorHandling
import lab.gabon.plugin.configureRouting
import lab.gabon.plugin.configureSerialization
import lab.gabon.repository.AdminUserRepo
import lab.gabon.repository.AdminUserRow
import lab.gabon.repository.AdminVideoListRow
import lab.gabon.repository.AdminVideoRepo
import lab.gabon.repository.AdminVideoRow
import lab.gabon.repository.CustomerRepo
import lab.gabon.repository.CustomerRow
import lab.gabon.repository.SocialRepo
import lab.gabon.service.AdminService
import lab.gabon.service.AuthService
import lab.gabon.service.CasResult
import lab.gabon.service.JwtService
import lab.gabon.service.RedisTokenStore
import lab.gabon.service.SocialService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Integration tests for admin routes using Ktor testApplication.
 * Mocks AdminUserRepo, AdminVideoRepo, CustomerRepo, RedisTokenStore (no real DB/Redis needed).
 * JwtService is real so we can verify actual token generation/parsing.
 */
class AdminRoutesTest {
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

    private val jwtService = JwtService(jwtConfig)
    private lateinit var tokenStore: RedisTokenStore
    private lateinit var adminUserRepo: AdminUserRepo
    private lateinit var adminVideoRepo: AdminVideoRepo
    private lateinit var customerRepo: CustomerRepo
    private lateinit var adminService: AdminService

    private val admin123Hash: String by lazy {
        at.favre.lib.crypto.bcrypt.BCrypt
            .withDefaults()
            .hashToString(4, "admin123".toCharArray())
    }

    private val now: Instant = Clock.System.now()

    private fun superadminRow(
        id: Long = 1L,
        username: String = "superadmin1",
        status: Short = AdminStatus.ACTIVE.value,
    ) = AdminUserRow(
        id = id,
        username = username,
        passwordHash = admin123Hash,
        role = AdminRole.SUPERADMIN.value,
        fullName = "Super Admin",
        phone = null,
        avatarUrl = null,
        status = status,
        lastLoginAt = null,
        createdAt = now,
        updatedAt = now,
    )

    private fun adminRow(
        id: Long = 2L,
        username: String = "admin1",
        status: Short = AdminStatus.ACTIVE.value,
    ) = AdminUserRow(
        id = id,
        username = username,
        passwordHash = admin123Hash,
        role = AdminRole.ADMIN.value,
        fullName = "Regular Admin",
        phone = null,
        avatarUrl = null,
        status = status,
        lastLoginAt = null,
        createdAt = now,
        updatedAt = now,
    )

    private fun makeAdminVideoListRow(
        id: Long = 1L,
        customerId: Long = 1L,
        status: Short = VideoStatus.PENDING_REVIEW.value,
        authorIsVip: Boolean = false,
        authorName: String? = "Alice",
    ) = AdminVideoListRow(
        id = id,
        customerId = customerId,
        title = "Test Video",
        fileName = "video.mp4",
        fileUrl = "https://stub.local/gabon-videos/1/abc.mp4",
        thumbnailUrl = null,
        mimeType = "video/mp4",
        status = status,
        reviewNotes = null,
        reviewedBy = null,
        totalClicks = 10,
        validClicks = 5,
        likeCount = 3,
        createdAt = now,
        authorName = authorName,
        authorAvatar = null,
        authorIsVip = authorIsVip,
    )

    private fun makeAdminVideoRow(
        id: Long = 42L,
        customerId: Long = 1L,
        status: Short = VideoStatus.PENDING_REVIEW.value,
    ) = AdminVideoRow(
        id = id,
        customerId = customerId,
        title = "Test Video",
        description = "A test video",
        fileName = "video.mp4",
        fileSize = 10_485_760,
        fileUrl = "https://stub.local/gabon-videos/1/abc.mp4",
        thumbnailUrl = null,
        previewGifUrl = null,
        mimeType = "video/mp4",
        duration = 120,
        width = 1920,
        height = 1080,
        status = status,
        reviewNotes = null,
        reviewedBy = null,
        reviewedAt = null,
        totalClicks = 10,
        validClicks = 5,
        likeCount = 3,
        createdAt = now,
        updatedAt = now,
        authorName = "Alice",
        authorAvatar = null,
        authorIsVip = false,
    )

    private fun makeCustomerRow(
        id: Long = 1L,
        username: String = "alice",
        isVip: Boolean = false,
    ) = CustomerRow(
        id = id,
        username = username,
        passwordHash = "hash",
        name = "Alice",
        phone = "1234567890",
        email = "alice@test.com",
        avatarUrl = null,
        signature = null,
        isVip = isVip,
        diamondBalance = 100,
        lastLoginAt = null,
        createdAt = now,
        updatedAt = now,
    )

    @OptIn(ExperimentalSerializationApi::class)
    private fun ApplicationTestBuilder.setupApp() {
        tokenStore = mockk()
        adminUserRepo = mockk()
        adminVideoRepo = mockk()
        customerRepo = mockk()
        adminService = AdminService(adminUserRepo, adminVideoRepo, customerRepo, jwtService, tokenStore)

        // Dummy services for the customer-side routes
        val authService = mockk<AuthService>()
        val socialRepo = mockk<SocialRepo>()
        val socialService = SocialService(socialRepo, customerRepo)

        application {
            configureSerialization()
            configureErrorHandling()
            configureAuthentication(jwtService, tokenStore)
            configureRouting(authService, socialService, customerRepo, adminService = adminService)
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

    /** Generate a superadmin access token (role=superadmin). */
    private fun superadminToken() = jwtService.generateAdminTokens(1L, "superadmin")

    /** Generate a regular admin access token (role=admin). */
    private fun regularAdminToken(adminId: Long = 2L) = jwtService.generateAdminTokens(adminId, "admin")

    // ═══════════════════════════════════════════════════════════
    // Admin Login
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `admin login - success returns 200 with tokens`() =
        testApplication {
            setupApp()
            coEvery { adminUserRepo.findByUsername("superadmin1") } returns superadminRow()
            coEvery { adminUserRepo.updateLastLogin(1L) } just Runs
            coEvery { tokenStore.setFamily(any(), any(), any(), any()) } just Runs

            val client = jsonClient()
            val response =
                client.post("/admin/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(AdminLoginRequest("superadmin1", "admin123"))
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            assertEquals(0, body["code"]?.jsonPrimitive?.int)
            val data = body["data"]?.jsonObject
            assertNotNull(data)
            assertNotNull(data["access_token"]?.jsonPrimitive?.content)
            assertNotNull(data["refresh_token"]?.jsonPrimitive?.content)

            coVerify { adminUserRepo.updateLastLogin(1L) }
        }

    @Test
    fun `admin login - disabled admin returns 403`() =
        testApplication {
            setupApp()
            coEvery { adminUserRepo.findByUsername("disabled_admin") } returns
                superadminRow(id = 3L, username = "disabled_admin", status = AdminStatus.DISABLED.value)

            val client = jsonClient()
            val response =
                client.post("/admin/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(AdminLoginRequest("disabled_admin", "admin123"))
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)

            val body = response.body<JsonObject>()
            assertEquals("account is disabled", body["message"]?.jsonPrimitive?.content)
        }

    @Test
    fun `admin login - wrong password returns 401`() =
        testApplication {
            setupApp()
            coEvery { adminUserRepo.findByUsername("superadmin1") } returns superadminRow()

            val client = jsonClient()
            val response =
                client.post("/admin/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(AdminLoginRequest("superadmin1", "wrongpass"))
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    // ═══════════════════════════════════════════════════════════
    // Admin Auth - me, logout, refresh
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `admin me - returns 200 with admin profile`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { adminUserRepo.findById(1L) } returns superadminRow()

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.get("/admin/v1/auth/me") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            val data = body["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(1, data["id"]?.jsonPrimitive?.int)
            assertEquals("superadmin1", data["username"]?.jsonPrimitive?.content)
        }

    @Test
    fun `admin me - customer token rejected on admin endpoint returns 401`() =
        testApplication {
            setupApp()

            val customerTokenPair = jwtService.generateCustomerTokens(1L)

            val client = jsonClient()
            val response =
                client.get("/admin/v1/auth/me") {
                    bearerAuth(customerTokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `admin logout - success`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { tokenStore.setBlacklist(any(), any()) } just Runs
            coEvery { tokenStore.deleteFamily(any()) } just Runs

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.post("/admin/v1/auth/logout") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            coVerify { tokenStore.setBlacklist(tokenPair.accessJti, any()) }
            coVerify { tokenStore.deleteFamily(tokenPair.familyId) }
        }

    @Test
    fun `admin refresh - success returns new tokens`() =
        testApplication {
            setupApp()

            val tokenPair = superadminToken()

            coEvery {
                tokenStore.casFamily(tokenPair.familyId, tokenPair.refreshJti, any())
            } returns CasResult.Success(userId = 1L)

            val client = jsonClient()
            val response =
                client.post("/admin/v1/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(AdminRefreshRequest(tokenPair.refreshToken))
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            val data = body["data"]?.jsonObject
            assertNotNull(data)
            assertNotNull(data["access_token"]?.jsonPrimitive?.content)
            assertNotNull(data["refresh_token"]?.jsonPrimitive?.content)
        }

    // ═══════════════════════════════════════════════════════════
    // Admin Video List with Filters
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `list videos - admin sees all statuses`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            val items = (1L..3L).map { makeAdminVideoListRow(id = it) }
            coEvery {
                adminVideoRepo.listVideosAdmin(1, 20, null, null, null, null, null)
            } returns (items to 3L)

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.get("/admin/v1/videos?page=1&page_size=20") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            val data = body["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(3, data["total"]?.jsonPrimitive?.int)
        }

    @Test
    fun `list videos - filter by status returns filtered results`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            val pending = listOf(makeAdminVideoListRow(id = 1, status = VideoStatus.PENDING_REVIEW.value))
            coEvery {
                adminVideoRepo.listVideosAdmin(1, 20, VideoStatus.PENDING_REVIEW.value, null, null, null, null)
            } returns (pending to 1L)

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.get("/admin/v1/videos?status=3") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            val data = body["data"]?.jsonObject
            val items = data?.get("items")?.jsonArray
            assertNotNull(items)
            assertEquals(1, items.size)
            assertEquals(3, items[0].jsonObject["status"]?.jsonPrimitive?.int)
        }

    @Test
    fun `list videos - filter by vip author`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            val vipItems = listOf(makeAdminVideoListRow(id = 1, authorIsVip = true))
            coEvery {
                adminVideoRepo.listVideosAdmin(1, 20, null, null, null, null, true)
            } returns (vipItems to 1L)

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.get("/admin/v1/videos?is_vip=true") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            val data = body["data"]?.jsonObject
            assertEquals(1, data?.get("total")?.jsonPrimitive?.int)
        }

    // ═══════════════════════════════════════════════════════════
    // Video Detail (Admin)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `video detail - admin returns full metadata`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { adminVideoRepo.getVideoDetailAdmin(42) } returns makeAdminVideoRow()

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.get("/admin/v1/videos/42") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            val data = body["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(42, data["id"]?.jsonPrimitive?.int)
            // Admin detail includes extra fields
            assertNotNull(data["file_name"])
            assertNotNull(data["file_size"])
        }

    // ═══════════════════════════════════════════════════════════
    // Review Video
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `approve video - success returns 200`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { adminVideoRepo.reviewVideo(42, 1L, VideoStatus.APPROVED.value, "Content is good") } returns true

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.post("/admin/v1/videos/42/review") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenPair.accessToken)
                    setBody(ReviewVideoRequest(status = 4, reviewNotes = "Content is good"))
                }

            assertEquals(HttpStatusCode.OK, response.status)

            coVerify { adminVideoRepo.reviewVideo(42, 1L, 4, "Content is good") }
        }

    @Test
    fun `reject video - success returns 200`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { adminVideoRepo.reviewVideo(42, 1L, VideoStatus.REJECTED.value, "Inappropriate content") } returns true

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.post("/admin/v1/videos/42/review") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenPair.accessToken)
                    setBody(ReviewVideoRequest(status = 5, reviewNotes = "Inappropriate content"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `review video - invalid status returns 400`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.post("/admin/v1/videos/42/review") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenPair.accessToken)
                    setBody(ReviewVideoRequest(status = 3))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)

            val body = response.body<JsonObject>()
            assertTrue(body["message"]?.jsonPrimitive?.content?.contains("4") == true)
        }

    // ═══════════════════════════════════════════════════════════
    // Admin Delete Video
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `admin delete video - success returns 200`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { adminVideoRepo.adminDeleteVideo(42) } returns true

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.delete("/admin/v1/videos/42") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            coVerify { adminVideoRepo.adminDeleteVideo(42) }
        }

    // ═══════════════════════════════════════════════════════════
    // Admin CRUD
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `create admin - superadmin success returns 201`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { adminUserRepo.findByUsername("newadmin") } returns null
            coEvery { adminUserRepo.create("newadmin", any(), 2, null) } returns 10L

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.post("/admin/v1/admin-users") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenPair.accessToken)
                    setBody(CreateAdminRequest("newadmin", "admin123", role = 2))
                }

            assertEquals(HttpStatusCode.Created, response.status)

            val body = response.body<JsonObject>()
            assertEquals(0, body["code"]?.jsonPrimitive?.int)
        }

    @Test
    fun `create admin - regular admin returns 403`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            val tokenPair = regularAdminToken()

            val client = jsonClient()
            val response =
                client.post("/admin/v1/admin-users") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenPair.accessToken)
                    setBody(CreateAdminRequest("newadmin", "admin123", role = 2))
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)

            val body = response.body<JsonObject>()
            assertEquals("superadmin required", body["message"]?.jsonPrimitive?.content)
        }

    @Test
    fun `list admins - returns paginated results`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            val admins = listOf(superadminRow(), adminRow())
            coEvery { adminUserRepo.listAdmins(1, 20, null, null, null) } returns (admins to 2L)

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.get("/admin/v1/admin-users") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            val data = body["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(2, data["total"]?.jsonPrimitive?.int)
        }

    @Test
    fun `get admin - returns admin by id`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { adminUserRepo.findById(2L) } returns adminRow()

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.get("/admin/v1/admin-users/2") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            val data = body["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(2, data["id"]?.jsonPrimitive?.int)
        }

    @Test
    fun `update admin - superadmin success`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { adminUserRepo.updateAdmin(2L, null, "New Name", null, null) } returns true

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.put("/admin/v1/admin-users/2") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenPair.accessToken)
                    setBody(UpdateAdminRequest(fullName = "New Name"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `delete admin - cannot delete self returns 400`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.delete("/admin/v1/admin-users/1") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)

            val body = response.body<JsonObject>()
            assertEquals("cannot delete yourself", body["message"]?.jsonPrimitive?.content)
        }

    @Test
    fun `delete admin - superadmin deletes other admin`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { adminUserRepo.softDelete(2L) } returns true

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.delete("/admin/v1/admin-users/2") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            coVerify { adminUserRepo.softDelete(2L) }
        }

    @Test
    fun `delete admin - regular admin returns 403`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            val tokenPair = regularAdminToken()

            val client = jsonClient()
            val response =
                client.delete("/admin/v1/admin-users/3") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)

            val body = response.body<JsonObject>()
            assertEquals("superadmin required", body["message"]?.jsonPrimitive?.content)
        }

    // ═══════════════════════════════════════════════════════════
    // Admin Password Change
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `change own password - regular admin success`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { adminUserRepo.updatePassword(2L, any()) } just Runs

            val tokenPair = regularAdminToken()

            val client = jsonClient()
            val response =
                client.put("/admin/v1/admin-users/2/password") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenPair.accessToken)
                    setBody(AdminChangePasswordRequest("newpass123"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            coVerify { adminUserRepo.updatePassword(2L, any()) }
        }

    @Test
    fun `change other admin password - regular admin returns 403`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            val tokenPair = regularAdminToken()

            val client = jsonClient()
            val response =
                client.put("/admin/v1/admin-users/3/password") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenPair.accessToken)
                    setBody(AdminChangePasswordRequest("newpass123"))
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)

            val body = response.body<JsonObject>()
            assertEquals("cannot change other admin's password", body["message"]?.jsonPrimitive?.content)
        }

    @Test
    fun `change other admin password - superadmin success`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { adminUserRepo.updatePassword(2L, any()) } just Runs

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.put("/admin/v1/admin-users/2/password") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenPair.accessToken)
                    setBody(AdminChangePasswordRequest("newpass123"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            coVerify { adminUserRepo.updatePassword(2L, any()) }
        }

    // ═══════════════════════════════════════════════════════════
    // Customer Management
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `list customers - with filters`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            val customers = listOf(makeCustomerRow(id = 1, isVip = true))
            coEvery { adminVideoRepo.listCustomers(1, 20, "alice", true) } returns (customers to 1L)

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.get("/admin/v1/customers?name=alice&is_vip=true") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            val data = body["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(1, data["total"]?.jsonPrimitive?.int)
        }

    @Test
    fun `reset customer password - success returns 200`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { customerRepo.findById(42) } returns makeCustomerRow(id = 42)
            coEvery { customerRepo.updatePassword(42, any()) } just Runs

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.put("/admin/v1/customers/42/password") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenPair.accessToken)
                    setBody(ResetCustomerPasswordRequest("resetpass123"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            coVerify { customerRepo.updatePassword(42, any()) }
        }

    @Test
    fun `reset customer password - nonexistent customer returns 404`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { customerRepo.findById(999) } returns null

            val tokenPair = superadminToken()

            val client = jsonClient()
            val response =
                client.put("/admin/v1/customers/999/password") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenPair.accessToken)
                    setBody(ResetCustomerPasswordRequest("resetpass123"))
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
}
