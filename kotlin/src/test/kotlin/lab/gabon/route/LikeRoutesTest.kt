package lab.gabon.route

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import lab.gabon.config.JwtConfig
import lab.gabon.config.S3Config
import lab.gabon.model.VideoStatus
import lab.gabon.plugin.PreserveAwareSnakeCase
import lab.gabon.plugin.configureAuthentication
import lab.gabon.plugin.configureErrorHandling
import lab.gabon.plugin.configureRouting
import lab.gabon.plugin.configureSerialization
import lab.gabon.repository.CustomerRepo
import lab.gabon.repository.PlayRecordRepo
import lab.gabon.repository.SocialRepo
import lab.gabon.repository.VideoRepo
import lab.gabon.repository.VideoRow
import lab.gabon.service.AuthService
import lab.gabon.service.JwtService
import lab.gabon.service.RedisTokenStore
import lab.gabon.service.SocialService
import lab.gabon.service.StorageService
import lab.gabon.service.VideoService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Integration tests for like/unlike endpoints (POST/DELETE /api/v1/videos/{id}/like).
 * Mocks VideoRepo and PlayRecordRepo; JwtService is real for token verification.
 */
class LikeRoutesTest {
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
    private lateinit var tokenStore: RedisTokenStore
    private lateinit var videoRepo: VideoRepo
    private lateinit var playRecordRepo: PlayRecordRepo
    private lateinit var videoService: VideoService
    private lateinit var storageService: StorageService

    private val now: Instant = Clock.System.now()

    private fun makeVideoRow(
        id: Long = 42L,
        customerId: Long = 1L,
        status: Short = VideoStatus.APPROVED.value,
        likeCount: Long = 5,
    ) = VideoRow(
        id = id,
        customerId = customerId,
        title = "Test Video",
        description = "A test video",
        fileName = "video.mp4",
        fileSize = 10_485_760,
        fileUrl = "https://stub.local/gabon-videos/1/abc.mp4",
        thumbnailUrl = null,
        mimeType = "video/mp4",
        duration = 120,
        status = status,
        totalClicks = 10,
        validClicks = 5,
        likeCount = likeCount,
        createdAt = now,
        updatedAt = now,
        uploaderName = "Alice",
        uploaderAvatar = null,
    )

    @OptIn(ExperimentalSerializationApi::class)
    private fun ApplicationTestBuilder.setupApp() {
        tokenStore = mockk()
        videoRepo = mockk()
        playRecordRepo = mockk()
        storageService = StorageService(s3Config)
        videoService = VideoService(videoRepo, playRecordRepo, storageService)

        val customerRepo = mockk<CustomerRepo>()
        val authService = mockk<AuthService>()
        val socialRepo = mockk<SocialRepo>()
        val socialService = SocialService(socialRepo, customerRepo)

        application {
            configureSerialization()
            configureErrorHandling()
            configureAuthentication(jwtService, tokenStore)
            configureRouting(authService, socialService, customerRepo, videoService)
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

    private fun aliceToken(): String = jwtService.generateCustomerTokens(1L).accessToken

    // ═══════════════════════════════════════════════════════════
    // Like
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `like approved video - 200`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { videoRepo.findById(42) } returns makeVideoRow()
            coEvery { videoRepo.likeVideo(42, 1L) } returns true

            val client = jsonClient()
            val response =
                client.post("/api/v1/videos/42/like") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<JsonObject>()
            assertEquals(0, body["code"]?.jsonPrimitive?.int)

            coVerify { videoRepo.likeVideo(42, 1L) }
        }

    @Test
    fun `like non-approved video - 403 VIDEO_NOT_APPROVED`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { videoRepo.findById(99) } returns
                makeVideoRow(
                    id = 99,
                    status = VideoStatus.PENDING_REVIEW.value,
                )

            val client = jsonClient()
            val response =
                client.post("/api/v1/videos/99/like") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            val body = response.body<JsonObject>()
            assertEquals("video not approved", body["message"]?.jsonPrimitive?.content)

            coVerify(exactly = 0) { videoRepo.likeVideo(any(), any()) }
        }

    @Test
    fun `like nonexistent video - 404 VIDEO_NOT_FOUND`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { videoRepo.findById(999999) } returns null

            val client = jsonClient()
            val response =
                client.post("/api/v1/videos/999999/like") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
            val body = response.body<JsonObject>()
            assertEquals("video not found", body["message"]?.jsonPrimitive?.content)

            coVerify(exactly = 0) { videoRepo.likeVideo(any(), any()) }
        }

    @Test
    fun `like already-liked video - idempotent 200, repo returns false`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { videoRepo.findById(42) } returns makeVideoRow(likeCount = 6)
            // CTE ON CONFLICT DO NOTHING -> no row inserted -> executeUpdate returns 0
            coEvery { videoRepo.likeVideo(42, 1L) } returns false

            val client = jsonClient()
            val response =
                client.post("/api/v1/videos/42/like") {
                    bearerAuth(aliceToken())
                }

            // Still 200 — idempotent behavior, no error for duplicate likes
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<JsonObject>()
            assertEquals(0, body["code"]?.jsonPrimitive?.int)

            coVerify { videoRepo.likeVideo(42, 1L) }
        }

    @Test
    fun `like without auth - 401`() =
        testApplication {
            setupApp()

            val client = jsonClient()
            val response = client.post("/api/v1/videos/42/like")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    // ═══════════════════════════════════════════════════════════
    // Unlike
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `unlike previously liked video - 200`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { videoRepo.unlikeVideo(42, 1L) } returns true

            val client = jsonClient()
            val response =
                client.delete("/api/v1/videos/42/like") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<JsonObject>()
            assertEquals(0, body["code"]?.jsonPrimitive?.int)

            coVerify { videoRepo.unlikeVideo(42, 1L) }
        }

    @Test
    fun `unlike not-liked video - idempotent 200`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            // CTE DELETE returns no rows -> UPDATE doesn't fire -> returns false
            coEvery { videoRepo.unlikeVideo(42, 1L) } returns false

            val client = jsonClient()
            val response =
                client.delete("/api/v1/videos/42/like") {
                    bearerAuth(aliceToken())
                }

            // Still 200 — idempotent, no error
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<JsonObject>()
            assertEquals(0, body["code"]?.jsonPrimitive?.int)

            coVerify { videoRepo.unlikeVideo(42, 1L) }
        }

    @Test
    fun `unlike without auth - 401`() =
        testApplication {
            setupApp()

            val client = jsonClient()
            val response = client.delete("/api/v1/videos/42/like")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
}
