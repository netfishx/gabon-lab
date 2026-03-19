package lab.gabon.route

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import lab.gabon.config.JwtConfig
import lab.gabon.plugin.PreserveAwareSnakeCase
import lab.gabon.plugin.configureAuthentication
import lab.gabon.plugin.configureErrorHandling
import lab.gabon.plugin.configureRouting
import lab.gabon.plugin.configureSerialization
import lab.gabon.repository.CustomerRepo
import lab.gabon.repository.ReportRepo
import lab.gabon.repository.RevenueReportRow
import lab.gabon.repository.SocialRepo
import lab.gabon.repository.VideoDailyReportRow
import lab.gabon.repository.VideoSummaryReportRow
import lab.gabon.service.AdminService
import lab.gabon.service.AuthService
import lab.gabon.service.JwtService
import lab.gabon.service.RedisTokenStore
import lab.gabon.service.ReportService
import lab.gabon.service.SocialService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ReportRoutesTest {
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
    private lateinit var reportRepo: ReportRepo
    private lateinit var reportService: ReportService

    @OptIn(ExperimentalSerializationApi::class)
    private fun ApplicationTestBuilder.setupApp() {
        tokenStore = mockk()
        reportRepo = mockk()
        reportService = ReportService(reportRepo)

        val customerRepo = mockk<CustomerRepo>()
        val authService = mockk<AuthService>()
        val socialRepo = mockk<SocialRepo>()
        val socialService = SocialService(socialRepo, customerRepo)

        application {
            configureSerialization()
            configureErrorHandling()
            configureAuthentication(jwtService, tokenStore)
            configureRouting(
                authService,
                socialService,
                customerRepo,
                reportService = reportService,
                adminService = mockk<AdminService>(),
            )
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

    private fun adminToken(): String {
        val tokenPair = jwtService.generateAdminTokens(1L, "superadmin")
        return tokenPair.accessToken
    }

    // ═══════════════════════════════════════════════════════════
    // Revenue Report
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `revenue report - returns date-grouped claim counts and diamond sums`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            coEvery { reportRepo.revenueReport("2026-03-01", "2026-03-19") } returns
                listOf(
                    RevenueReportRow(date = "2026-03-15", claimCount = 5, totalDiamonds = 120),
                    RevenueReportRow(date = "2026-03-16", claimCount = 3, totalDiamonds = 80),
                )

            val client = jsonClient()
            val response =
                client.get("/admin/v1/reports/revenue?start_date=2026-03-01&end_date=2026-03-19") {
                    bearerAuth(adminToken())
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            assertEquals(0, body["code"]?.jsonPrimitive?.int)
            val data = body["data"]?.jsonArray
            assertNotNull(data)
            assertEquals(2, data.size)

            val first = data[0].jsonObject
            assertEquals("2026-03-15", first["date"]?.jsonPrimitive?.content)
            assertEquals(5, first["claim_count"]?.jsonPrimitive?.long)
            assertEquals(120, first["total_diamonds"]?.jsonPrimitive?.long)

            val second = data[1].jsonObject
            assertEquals("2026-03-16", second["date"]?.jsonPrimitive?.content)
            assertEquals(3, second["claim_count"]?.jsonPrimitive?.long)
            assertEquals(80, second["total_diamonds"]?.jsonPrimitive?.long)
        }

    // ═══════════════════════════════════════════════════════════
    // Video Daily Report
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `video daily report - returns date-grouped upload and engagement counts`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            coEvery { reportRepo.videoDailyReport("2026-03-01", "2026-03-19") } returns
                listOf(
                    VideoDailyReportRow(
                        date = "2026-03-15",
                        uploadCount = 10,
                        totalClicks = 500,
                        totalValidClicks = 300,
                        totalLikes = 50,
                    ),
                    VideoDailyReportRow(
                        date = "2026-03-16",
                        uploadCount = 8,
                        totalClicks = 400,
                        totalValidClicks = 250,
                        totalLikes = 40,
                    ),
                )

            val client = jsonClient()
            val response =
                client.get("/admin/v1/reports/video/daily?start_date=2026-03-01&end_date=2026-03-19") {
                    bearerAuth(adminToken())
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            assertEquals(0, body["code"]?.jsonPrimitive?.int)
            val data = body["data"]?.jsonArray
            assertNotNull(data)
            assertEquals(2, data.size)

            val first = data[0].jsonObject
            assertEquals("2026-03-15", first["date"]?.jsonPrimitive?.content)
            assertEquals(10, first["upload_count"]?.jsonPrimitive?.long)
            assertEquals(500, first["total_clicks"]?.jsonPrimitive?.long)
            assertEquals(300, first["total_valid_clicks"]?.jsonPrimitive?.long)
            assertEquals(50, first["total_likes"]?.jsonPrimitive?.long)
        }

    // ═══════════════════════════════════════════════════════════
    // Video Summary Report
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `video summary report - returns aggregate totals with status breakdowns`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            coEvery { reportRepo.videoSummaryReport("2026-03-01", "2026-03-19") } returns
                VideoSummaryReportRow(
                    totalVideos = 100,
                    approvedCount = 60,
                    pendingCount = 25,
                    rejectedCount = 15,
                    totalClicks = 10000,
                    totalValidClicks = 6000,
                    totalLikes = 1500,
                )

            val client = jsonClient()
            val response =
                client.get("/admin/v1/reports/video/summary?start_date=2026-03-01&end_date=2026-03-19") {
                    bearerAuth(adminToken())
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val body = response.body<JsonObject>()
            assertEquals(0, body["code"]?.jsonPrimitive?.int)
            val data = body["data"]?.jsonObject
            assertNotNull(data)

            assertEquals(100, data["total_videos"]?.jsonPrimitive?.long)
            assertEquals(60, data["approved_count"]?.jsonPrimitive?.long)
            assertEquals(25, data["pending_count"]?.jsonPrimitive?.long)
            assertEquals(15, data["rejected_count"]?.jsonPrimitive?.long)
            assertEquals(10000, data["total_clicks"]?.jsonPrimitive?.long)
            assertEquals(6000, data["total_valid_clicks"]?.jsonPrimitive?.long)
            assertEquals(1500, data["total_likes"]?.jsonPrimitive?.long)
        }
}
