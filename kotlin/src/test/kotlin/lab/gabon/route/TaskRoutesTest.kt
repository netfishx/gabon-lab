package lab.gabon.route

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import lab.gabon.config.JwtConfig
import lab.gabon.model.AppError
import lab.gabon.model.AppException
import lab.gabon.model.TaskType
import lab.gabon.plugin.configureAuthentication
import lab.gabon.plugin.configureErrorHandling
import lab.gabon.plugin.configureRouting
import lab.gabon.plugin.configureSerialization
import lab.gabon.repository.*
import lab.gabon.service.*
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class TaskRoutesTest {

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
    private lateinit var tokenStore: RedisTokenStore
    private lateinit var taskRepo: TaskRepo
    private lateinit var signInRepo: SignInRepo
    private lateinit var taskService: TaskService

    private fun dailyTaskDef(id: Long = 1L) = TaskDefinitionRow(
        id = id, taskCode = "daily_watch", taskName = "Watch 3 videos",
        description = null, taskType = TaskType.DAILY.value,
        taskCategory = 1, targetCount = 3, rewardDiamonds = 10,
        iconUrl = null, displayOrder = 1, vipOnly = false, status = 1,
    )

    private fun weeklyTaskDef(id: Long = 2L) = TaskDefinitionRow(
        id = id, taskCode = "weekly_share", taskName = "Share 5 videos",
        description = null, taskType = TaskType.WEEKLY.value,
        taskCategory = 2, targetCount = 5, rewardDiamonds = 30,
        iconUrl = null, displayOrder = 2, vipOnly = false, status = 1,
    )

    private fun monthlyTaskDef(id: Long = 3L) = TaskDefinitionRow(
        id = id, taskCode = "monthly_upload", taskName = "Upload 10 videos",
        description = null, taskType = TaskType.MONTHLY.value,
        taskCategory = 3, targetCount = 10, rewardDiamonds = 100,
        iconUrl = null, displayOrder = 3, vipOnly = false, status = 1,
    )

    private fun progressRow(
        id: Long = 1L,
        taskId: Long = 1L,
        currentCount: Int = 0,
        taskStatus: Short = 1,
        periodKey: String = "2026-03-19",
    ) = TaskProgressRow(
        id = id, customerId = 1L, taskId = taskId,
        currentCount = currentCount, targetCount = 3,
        periodKey = periodKey, taskStatus = taskStatus,
        rewardDiamonds = 10, completedAt = null, claimedAt = null,
        createdAt = kotlinx.datetime.Clock.System.now(),
    )

    private fun taskWithProgress(
        taskId: Long = 1L,
        taskCode: String = "daily_watch",
        taskName: String = "Watch 3 videos",
        taskType: Short = 1,
        progressId: Long = 10L,
        currentCount: Int = 0,
        taskStatus: Short = 1,
        periodKey: String = "2026-03-19",
    ) = TaskWithProgressRow(
        taskId = taskId, taskCode = taskCode, taskName = taskName,
        taskType = taskType, taskCategory = 1, targetCount = 3,
        rewardDiamonds = 10, iconUrl = null, progressId = progressId,
        currentCount = currentCount, taskStatus = taskStatus,
        periodKey = periodKey,
    )

    @OptIn(ExperimentalSerializationApi::class)
    private fun ApplicationTestBuilder.setupApp() {
        tokenStore = mockk()
        taskRepo = mockk()
        signInRepo = mockk()
        taskService = TaskService(taskRepo, signInRepo)

        val customerRepo = mockk<CustomerRepo>()
        val authService = mockk<AuthService>()
        val socialRepo = mockk<SocialRepo>()
        val socialService = SocialService(socialRepo, customerRepo)

        application {
            configureSerialization()
            configureErrorHandling()
            configureAuthentication(jwtService, tokenStore)
            configureRouting(authService, socialService, customerRepo, taskService = taskService)
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

    private fun aliceToken(): String {
        val tokenPair = jwtService.generateCustomerTokens(1L)
        return tokenPair.accessToken
    }

    // =====================================================
    // GET /tasks - List Tasks
    // =====================================================

    @Test
    fun `list all active tasks with auto-created progress - 200`() = testApplication {
        setupApp()
        coEvery { tokenStore.isBlacklisted(any()) } returns false

        val defs = listOf(dailyTaskDef(), weeklyTaskDef(), monthlyTaskDef())
        coEvery { taskRepo.findActiveTaskDefinitions(null) } returns defs
        coEvery { taskRepo.findActiveTaskDefinitions(TaskType.DAILY.value) } returns listOf(dailyTaskDef())
        coEvery { taskRepo.findActiveTaskDefinitions(TaskType.WEEKLY.value) } returns listOf(weeklyTaskDef())
        coEvery { taskRepo.findActiveTaskDefinitions(TaskType.MONTHLY.value) } returns listOf(monthlyTaskDef())

        // Upsert progress for each
        coEvery { taskRepo.upsertProgress(1L, 1L, any(), 3, 10) } returns progressRow(id = 10)
        coEvery { taskRepo.upsertProgress(1L, 2L, any(), 5, 30) } returns progressRow(id = 20, taskId = 2L)
        coEvery { taskRepo.upsertProgress(1L, 3L, any(), 10, 100) } returns progressRow(id = 30, taskId = 3L)

        // List progress for each type
        coEvery { taskRepo.listProgressWithTasks(1L, any(), TaskType.DAILY.value, null) } returns listOf(
            taskWithProgress(progressId = 10),
        )
        coEvery { taskRepo.listProgressWithTasks(1L, any(), TaskType.WEEKLY.value, null) } returns listOf(
            taskWithProgress(taskId = 2, taskCode = "weekly_share", taskName = "Share 5 videos", taskType = 2, progressId = 20),
        )
        coEvery { taskRepo.listProgressWithTasks(1L, any(), TaskType.MONTHLY.value, null) } returns listOf(
            taskWithProgress(taskId = 3, taskCode = "monthly_upload", taskName = "Upload 10 videos", taskType = 3, progressId = 30),
        )

        val client = jsonClient()
        val response = client.get("/api/v1/tasks") {
            bearerAuth(aliceToken())
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<JsonObject>()
        assertEquals(0, body["code"]?.jsonPrimitive?.int)
        val data = body["data"]?.jsonArray
        assertNotNull(data)
        assertEquals(3, data.size)

        // Verify each item has required fields
        val first = data[0].jsonObject
        assertNotNull(first["task_id"])
        assertNotNull(first["task_code"])
        assertNotNull(first["task_name"])
        assertNotNull(first["task_type"])
        assertNotNull(first["target_count"])
        assertNotNull(first["reward_diamonds"])
        assertNotNull(first["progress_id"])
        assertNotNull(first["current_count"])
        assertNotNull(first["task_status"])
    }

    @Test
    fun `filter tasks by type - only daily returned`() = testApplication {
        setupApp()
        coEvery { tokenStore.isBlacklisted(any()) } returns false

        coEvery { taskRepo.findActiveTaskDefinitions(TaskType.DAILY.value) } returns listOf(dailyTaskDef())
        coEvery { taskRepo.upsertProgress(1L, 1L, any(), 3, 10) } returns progressRow(id = 10)
        coEvery { taskRepo.listProgressWithTasks(1L, any(), TaskType.DAILY.value, null) } returns listOf(
            taskWithProgress(progressId = 10),
        )

        val client = jsonClient()
        val response = client.get("/api/v1/tasks?task_type=1") {
            bearerAuth(aliceToken())
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = response.body<JsonObject>()["data"]?.jsonArray
        assertNotNull(data)
        assertEquals(1, data.size)
        assertEquals(1, data[0].jsonObject["task_type"]?.jsonPrimitive?.int)
    }

    @Test
    fun `filter tasks by status - only completed returned`() = testApplication {
        setupApp()
        coEvery { tokenStore.isBlacklisted(any()) } returns false

        val defs = listOf(dailyTaskDef(), weeklyTaskDef())
        coEvery { taskRepo.findActiveTaskDefinitions(null) } returns defs
        coEvery { taskRepo.findActiveTaskDefinitions(TaskType.DAILY.value) } returns listOf(dailyTaskDef())
        coEvery { taskRepo.findActiveTaskDefinitions(TaskType.WEEKLY.value) } returns listOf(weeklyTaskDef())
        coEvery { taskRepo.upsertProgress(1L, any(), any(), any(), any()) } returns progressRow()

        // Only daily has a completed task
        coEvery { taskRepo.listProgressWithTasks(1L, any(), TaskType.DAILY.value, 2) } returns listOf(
            taskWithProgress(progressId = 10, taskStatus = 2, currentCount = 3),
        )
        coEvery { taskRepo.listProgressWithTasks(1L, any(), TaskType.WEEKLY.value, 2) } returns emptyList()

        val client = jsonClient()
        val response = client.get("/api/v1/tasks?task_status=2") {
            bearerAuth(aliceToken())
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = response.body<JsonObject>()["data"]?.jsonArray
        assertNotNull(data)
        assertEquals(1, data.size)
        assertEquals(2, data[0].jsonObject["task_status"]?.jsonPrimitive?.int)
    }

    @Test
    fun `list tasks - 401 without auth`() = testApplication {
        setupApp()

        val client = jsonClient()
        val response = client.get("/api/v1/tasks")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // =====================================================
    // POST /tasks/{progressId}/claim
    // =====================================================

    @Test
    fun `claim completed task - 200 with diamonds`() = testApplication {
        setupApp()
        coEvery { tokenStore.isBlacklisted(any()) } returns false
        coEvery { taskRepo.claimReward(10L, 1L) } returns 50

        val client = jsonClient()
        val response = client.post("/api/v1/tasks/10/claim") {
            bearerAuth(aliceToken())
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = response.body<JsonObject>()["data"]?.jsonObject
        assertNotNull(data)
        assertEquals(50, data["diamonds"]?.jsonPrimitive?.int)
    }

    @Test
    fun `claim in-progress task - 400 TASK_NOT_CLAIMABLE`() = testApplication {
        setupApp()
        coEvery { tokenStore.isBlacklisted(any()) } returns false
        coEvery { taskRepo.claimReward(10L, 1L) } throws AppException(AppError.TaskNotClaimable("task is not completed"))

        val client = jsonClient()
        val response = client.post("/api/v1/tasks/10/claim") {
            bearerAuth(aliceToken())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.body<JsonObject>()
        assertEquals("task is not completed", body["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun `claim already-claimed task - 400 TASK_NOT_CLAIMABLE`() = testApplication {
        setupApp()
        coEvery { tokenStore.isBlacklisted(any()) } returns false
        coEvery { taskRepo.claimReward(10L, 1L) } throws AppException(AppError.TaskNotClaimable("task already claimed"))

        val client = jsonClient()
        val response = client.post("/api/v1/tasks/10/claim") {
            bearerAuth(aliceToken())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.body<JsonObject>()
        assertEquals("task already claimed", body["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun `claim another user's task - 400 TASK_NOT_CLAIMABLE`() = testApplication {
        setupApp()
        coEvery { tokenStore.isBlacklisted(any()) } returns false
        coEvery { taskRepo.claimReward(10L, 1L) } throws AppException(AppError.TaskNotClaimable("task progress not found"))

        val client = jsonClient()
        val response = client.post("/api/v1/tasks/10/claim") {
            bearerAuth(aliceToken())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.body<JsonObject>()
        assertEquals("task progress not found", body["message"]?.jsonPrimitive?.content)
    }

    // =====================================================
    // POST /activity/sign-in
    // =====================================================

    @Test
    fun `sign in - 200 with diamonds`() = testApplication {
        setupApp()
        coEvery { tokenStore.isBlacklisted(any()) } returns false

        val signInRecord = SignInRecordRow(
            id = 1L, customerId = 1L, periodKey = "2026-03-19",
            rewardDiamonds = 1, createdAt = kotlinx.datetime.Clock.System.now(),
        )
        coEvery { signInRepo.signIn(1L, any(), 1) } returns signInRecord

        val client = jsonClient()
        val response = client.post("/api/v1/activity/sign-in") {
            bearerAuth(aliceToken())
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = response.body<JsonObject>()["data"]?.jsonObject
        assertNotNull(data)
        assertEquals(1, data["diamonds"]?.jsonPrimitive?.int)
    }

    @Test
    fun `duplicate sign-in - 409 ALREADY_SIGNED_IN`() = testApplication {
        setupApp()
        coEvery { tokenStore.isBlacklisted(any()) } returns false
        coEvery { signInRepo.signIn(1L, any(), 1) } throws AppException(AppError.AlreadySignedIn())

        val client = jsonClient()
        val response = client.post("/api/v1/activity/sign-in") {
            bearerAuth(aliceToken())
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val body = response.body<JsonObject>()
        assertEquals("already signed in today", body["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun `sign in without auth - 401`() = testApplication {
        setupApp()

        val client = jsonClient()
        val response = client.post("/api/v1/activity/sign-in")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
