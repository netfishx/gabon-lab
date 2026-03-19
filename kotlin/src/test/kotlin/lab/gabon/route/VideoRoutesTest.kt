package lab.gabon.route

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
import lab.gabon.repository.VideoListRow
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Integration tests for video routes using Ktor testApplication.
 * Mocks VideoRepo, PlayRecordRepo, StorageService (no real DB/S3 needed).
 * JwtService is real so we can verify actual token generation/parsing.
 */
class VideoRoutesTest {
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
        id: Long = 1L,
        customerId: Long = 1L,
        title: String? = "Test Video",
        status: Short = VideoStatus.APPROVED.value,
        totalClicks: Long = 10,
        validClicks: Long = 5,
        likeCount: Long = 3,
    ) = VideoRow(
        id = id,
        customerId = customerId,
        title = title,
        description = "A test video description",
        fileName = "video.mp4",
        fileSize = 10_485_760,
        fileUrl = "https://stub.local/gabon-videos/1/abc.mp4",
        thumbnailUrl = null,
        mimeType = "video/mp4",
        duration = 120,
        status = status,
        totalClicks = totalClicks,
        validClicks = validClicks,
        likeCount = likeCount,
        createdAt = now,
        updatedAt = now,
        uploaderName = "Alice",
        uploaderAvatar = null,
    )

    private fun makeVideoListRow(
        id: Long = 1L,
        customerId: Long = 1L,
        title: String? = "Test Video",
        status: Short = VideoStatus.APPROVED.value,
    ) = VideoListRow(
        id = id,
        customerId = customerId,
        title = title,
        fileName = "video.mp4",
        fileUrl = "https://stub.local/gabon-videos/1/abc.mp4",
        thumbnailUrl = null,
        mimeType = "video/mp4",
        status = status,
        totalClicks = 10,
        validClicks = 5,
        likeCount = 3,
        createdAt = now,
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

        // Dummy auth and social services for routing setup
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

    // ═══════════════════════════════════════════════════════════
    // Presigned Upload URL
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `presign upload - success returns 200 with uploadUrl, fileUrl, s3Key`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            val tokenPair = jwtService.generateCustomerTokens(1L)

            val client = jsonClient()
            val response =
                client.post("/api/v1/videos/upload-url") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenPair.accessToken)
                    setBody(PresignUploadRequest("my-video.mp4", "video/mp4"))
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            assertEquals(0, body["code"]?.jsonPrimitive?.int)
            val data = body["data"]?.jsonObject
            assertNotNull(data)
            assertNotNull(data["uploadUrl"]?.jsonPrimitive?.content)
            assertNotNull(data["fileUrl"]?.jsonPrimitive?.content)
            val s3Key = data["s3Key"]?.jsonPrimitive?.content
            assertNotNull(s3Key)
            // s3Key should follow pattern: {customerId}/{uuid}.mp4
            assertTrue(s3Key.startsWith("1/"), "s3Key should start with customerId: $s3Key")
            assertTrue(s3Key.endsWith(".mp4"), "s3Key should end with .mp4: $s3Key")
        }

    @Test
    fun `presign upload - without auth returns 401`() =
        testApplication {
            setupApp()

            val client = jsonClient()
            val response =
                client.post("/api/v1/videos/upload-url") {
                    contentType(ContentType.Application.Json)
                    setBody(PresignUploadRequest("my-video.mp4", "video/mp4"))
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    // ═══════════════════════════════════════════════════════════
    // Confirm Upload
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `confirm upload - success returns 201 with status 3`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { videoRepo.create(any(), any(), any(), any(), any(), any(), any(), any()) } returns 42L

            val tokenPair = jwtService.generateCustomerTokens(1L)

            val client = jsonClient()
            val response =
                client.post("/api/v1/videos/confirm-upload") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(tokenPair.accessToken)
                    setBody(
                        ConfirmUploadRequest(
                            s3Key = "1/abc.mp4",
                            fileName = "my-video.mp4",
                            fileSize = 10_485_760,
                            mimeType = "video/mp4",
                            title = "My First Video",
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Created, response.status)

            val body = response.body<JsonObject>()
            assertEquals(0, body["code"]?.jsonPrimitive?.int)
            val data = body["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(42, data["videoId"]?.jsonPrimitive?.int)
            assertEquals(3, data["status"]?.jsonPrimitive?.int)

            coVerify {
                videoRepo.create(
                    customerId = 1L,
                    title = "My First Video",
                    description = null,
                    fileName = "my-video.mp4",
                    fileSize = 10_485_760,
                    fileUrl = any(),
                    mimeType = "video/mp4",
                    status = VideoStatus.PENDING_REVIEW.value,
                )
            }
        }

    // ═══════════════════════════════════════════════════════════
    // List Approved Videos (Public)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `list videos - returns approved videos with pagination`() =
        testApplication {
            setupApp()
            val items = (1L..3L).map { makeVideoListRow(id = it) }
            coEvery { videoRepo.listApproved(1, 20, null) } returns (items to 3L)

            val client = jsonClient()
            val response = client.get("/api/v1/videos?page=1&page_size=20")

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            assertEquals(0, body["code"]?.jsonPrimitive?.int)
            val data = body["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(3, data["total"]?.jsonPrimitive?.int)
            assertEquals(1, data["page"]?.jsonPrimitive?.int)
            assertEquals(20, data["pageSize"]?.jsonPrimitive?.int)
            val itemsArray = data["items"]?.jsonArray
            assertNotNull(itemsArray)
            assertEquals(3, itemsArray.size)
        }

    @Test
    fun `list videos - keyword search filters results`() =
        testApplication {
            setupApp()
            val catVideo = makeVideoListRow(id = 1, title = "Cute Cat Dancing")
            coEvery { videoRepo.listApproved(1, 10, "cat") } returns (listOf(catVideo) to 1L)

            val client = jsonClient()
            val response = client.get("/api/v1/videos?keyword=cat")

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            val data = body["data"]?.jsonObject
            val items = data?.get("items")?.jsonArray
            assertNotNull(items)
            assertEquals(1, items.size)
            assertEquals("Cute Cat Dancing", items[0].jsonObject["title"]?.jsonPrimitive?.content)
        }

    // ═══════════════════════════════════════════════════════════
    // Featured Videos
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `list featured - returns featured videos sorted by likes`() =
        testApplication {
            setupApp()
            val items = (1L..2L).map { makeVideoListRow(id = it) }
            coEvery { videoRepo.listFeatured(1, 10) } returns (items to 2L)

            val client = jsonClient()
            val response = client.get("/api/v1/videos/featured?page=1&page_size=10")

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            assertEquals(0, body["code"]?.jsonPrimitive?.int)
            val data = body["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(2, data["total"]?.jsonPrimitive?.int)
        }

    // ═══════════════════════════════════════════════════════════
    // Video Detail
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `video detail - public access returns 200 with isLiked false`() =
        testApplication {
            setupApp()
            val video = makeVideoRow(id = 42)
            coEvery { videoRepo.findById(42) } returns video

            val client = jsonClient()
            val response = client.get("/api/v1/videos/42")

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            val data = body["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(42, data["id"]?.jsonPrimitive?.int)
            assertEquals(false, data["is_liked"]?.jsonPrimitive?.boolean)
        }

    @Test
    fun `video detail - authenticated user who liked sees isLiked true`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            val video = makeVideoRow(id = 42, customerId = 99)
            coEvery { videoRepo.findById(42) } returns video
            coEvery { videoRepo.isLikedBy(42, 1L) } returns true

            val tokenPair = jwtService.generateCustomerTokens(1L)

            val client = jsonClient()
            val response =
                client.get("/api/v1/videos/42") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            val data = body["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(true, data["is_liked"]?.jsonPrimitive?.boolean)
        }

    @Test
    fun `video detail - owner can see unapproved video`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            val video = makeVideoRow(id = 99, customerId = 1L, status = VideoStatus.PENDING_REVIEW.value)
            coEvery { videoRepo.findById(99) } returns video
            coEvery { videoRepo.isLikedBy(99, 1L) } returns false

            val tokenPair = jwtService.generateCustomerTokens(1L)

            val client = jsonClient()
            val response =
                client.get("/api/v1/videos/99") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `video detail - non-owner gets 403 for unapproved video`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            // Video belongs to customer 1, but requester is customer 2
            val video = makeVideoRow(id = 99, customerId = 1L, status = VideoStatus.PENDING_REVIEW.value)
            coEvery { videoRepo.findById(99) } returns video

            val tokenPair = jwtService.generateCustomerTokens(2L)

            val client = jsonClient()
            val response =
                client.get("/api/v1/videos/99") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)

            val body = response.body<JsonObject>()
            assertEquals("video not approved", body["message"]?.jsonPrimitive?.content)
        }

    @Test
    fun `video detail - nonexistent video returns 404`() =
        testApplication {
            setupApp()
            coEvery { videoRepo.findById(999999) } returns null

            val client = jsonClient()
            val response = client.get("/api/v1/videos/999999")

            assertEquals(HttpStatusCode.NotFound, response.status)

            val body = response.body<JsonObject>()
            assertEquals("video not found", body["message"]?.jsonPrimitive?.content)
        }

    // ═══════════════════════════════════════════════════════════
    // My Videos
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `my videos - returns all statuses for owner with status filter`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            val pendingVideos =
                (1L..2L).map {
                    makeVideoListRow(id = it, customerId = 1L, status = VideoStatus.PENDING_REVIEW.value)
                }
            coEvery { videoRepo.listByCustomer(1L, 1, 10, VideoStatus.PENDING_REVIEW.value) } returns (pendingVideos to 2L)

            val tokenPair = jwtService.generateCustomerTokens(1L)

            val client = jsonClient()
            val response =
                client.get("/api/v1/videos/me?status=3") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            val data = body["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(2, data["total"]?.jsonPrimitive?.int)
        }

    // ═══════════════════════════════════════════════════════════
    // User Videos (Public)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `user videos - returns only approved videos`() =
        testApplication {
            setupApp()
            val approvedVideos =
                (1L..5L).map {
                    makeVideoListRow(id = it, customerId = 42L)
                }
            coEvery { videoRepo.listApprovedByUser(42, 1, 10) } returns (approvedVideos to 5L)

            val client = jsonClient()
            val response = client.get("/api/v1/users/42/videos")

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            val data = body["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(5, data["total"]?.jsonPrimitive?.int)
        }

    // ═══════════════════════════════════════════════════════════
    // Delete Video
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `delete own video - success returns 200`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { videoRepo.softDelete(42, 1L) } returns true

            val tokenPair = jwtService.generateCustomerTokens(1L)

            val client = jsonClient()
            val response =
                client.delete("/api/v1/videos/42") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            coVerify { videoRepo.softDelete(42, 1L) }
        }

    @Test
    fun `delete video - not owner returns 404`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            // softDelete returns false when customer_id doesn't match
            coEvery { videoRepo.softDelete(42, 2L) } returns false

            val tokenPair = jwtService.generateCustomerTokens(2L)

            val client = jsonClient()
            val response =
                client.delete("/api/v1/videos/42") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    // ═══════════════════════════════════════════════════════════
    // Play Click
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `play click - increments total_clicks and creates record`() =
        testApplication {
            setupApp()
            coEvery { videoRepo.incrementTotalClicks(42) } just Runs
            coEvery { playRecordRepo.create(42, null, 1, any()) } just Runs

            val client = jsonClient()
            val response = client.post("/api/v1/videos/42/play-click")

            assertEquals(HttpStatusCode.OK, response.status)

            coVerify { videoRepo.incrementTotalClicks(42) }
            coVerify { playRecordRepo.create(42, null, 1, any()) }
        }

    @Test
    fun `play click - with auth passes customerId`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { videoRepo.incrementTotalClicks(42) } just Runs
            coEvery { playRecordRepo.create(42, 1L, 1, any()) } just Runs

            val tokenPair = jwtService.generateCustomerTokens(1L)

            val client = jsonClient()
            val response =
                client.post("/api/v1/videos/42/play-click") {
                    bearerAuth(tokenPair.accessToken)
                }

            assertEquals(HttpStatusCode.OK, response.status)

            coVerify { playRecordRepo.create(42, 1L, 1, any()) }
        }

    // ═══════════════════════════════════════════════════════════
    // Valid Play
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `valid play - increments valid_clicks and creates record`() =
        testApplication {
            setupApp()
            coEvery { videoRepo.incrementValidClicks(42) } just Runs
            coEvery { playRecordRepo.create(42, null, 2, any()) } just Runs

            val client = jsonClient()
            val response = client.post("/api/v1/videos/42/play-valid")

            assertEquals(HttpStatusCode.OK, response.status)

            coVerify { videoRepo.incrementValidClicks(42) }
            coVerify { playRecordRepo.create(42, null, 2, any()) }
        }
}
