package lab.gabon.route

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
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
import lab.gabon.repository.CustomerRow
import lab.gabon.repository.FollowUserRow
import lab.gabon.repository.SocialRepo
import lab.gabon.service.AuthService
import lab.gabon.service.JwtService
import lab.gabon.service.RedisTokenStore
import lab.gabon.service.SocialService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class SocialRoutesTest {
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
    private lateinit var customerRepo: CustomerRepo
    private lateinit var socialRepo: SocialRepo
    private lateinit var tokenStore: RedisTokenStore
    private lateinit var socialService: SocialService

    private val now = Clock.System.now()

    private fun customerRow(
        id: Long,
        username: String,
        name: String? = null,
    ) = CustomerRow(
        id = id,
        username = username,
        passwordHash = "hash",
        name = name,
        phone = null,
        email = null,
        avatarUrl = null,
        signature = "hello",
        isVip = false,
        diamondBalance = 0,
        lastLoginAt = null,
        createdAt = now,
        updatedAt = now,
    )

    @OptIn(ExperimentalSerializationApi::class)
    private fun ApplicationTestBuilder.setupApp() {
        customerRepo = mockk()
        socialRepo = mockk()
        tokenStore = mockk()
        socialService = SocialService(socialRepo, customerRepo)
        val authService = AuthService(customerRepo, jwtService, tokenStore)

        application {
            configureSerialization()
            configureErrorHandling()
            configureAuthentication(jwtService, tokenStore)
            configureRouting(authService, socialService, customerRepo)
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

    private fun bobToken(): String {
        val tokenPair = jwtService.generateCustomerTokens(2L)
        return tokenPair.accessToken
    }

    // ═══════════════════════════════════════════════════════════
    // Follow
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `follow another user - 200`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { customerRepo.findById(2L) } returns customerRow(2, "bob")
            coEvery { socialRepo.follow(1L, 2L) } returns true

            val client = jsonClient()
            val response =
                client.post("/api/v1/users/2/follow") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<JsonObject>()
            assertEquals(0, body["code"]?.jsonPrimitive?.int)
        }

    @Test
    fun `follow nonexistent user - 404 NOT_FOUND`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { customerRepo.findById(999999L) } returns null

            val client = jsonClient()
            val response =
                client.post("/api/v1/users/999999/follow") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
            val body = response.body<JsonObject>()
            assertEquals("user not found", body["message"]?.jsonPrimitive?.content)
        }

    @Test
    fun `follow self - 400 USER_CANNOT_FOLLOW_SELF`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            val client = jsonClient()
            val response =
                client.post("/api/v1/users/1/follow") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.body<JsonObject>()
            assertEquals("cannot follow self", body["message"]?.jsonPrimitive?.content)
        }

    @Test
    fun `follow already followed user - 409 USER_ALREADY_FOLLOWING`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { customerRepo.findById(2L) } returns customerRow(2, "bob")
            coEvery { socialRepo.follow(1L, 2L) } returns false // ON CONFLICT DO NOTHING

            val client = jsonClient()
            val response =
                client.post("/api/v1/users/2/follow") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
            val body = response.body<JsonObject>()
            assertEquals("already following", body["message"]?.jsonPrimitive?.content)
        }

    // ═══════════════════════════════════════════════════════════
    // Unfollow
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `unfollow a user - 200`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { socialRepo.unfollow(1L, 2L) } returns true

            val client = jsonClient()
            val response =
                client.delete("/api/v1/users/2/follow") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<JsonObject>()
            assertEquals(0, body["code"]?.jsonPrimitive?.int)
        }

    @Test
    fun `unfollow self - 400 USER_CANNOT_FOLLOW_SELF`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false

            val client = jsonClient()
            val response =
                client.delete("/api/v1/users/1/follow") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.body<JsonObject>()
            assertEquals("cannot follow self", body["message"]?.jsonPrimitive?.content)
        }

    @Test
    fun `unfollow not following - 400 USER_NOT_FOLLOWING`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { socialRepo.unfollow(1L, 2L) } returns false

            val client = jsonClient()
            val response =
                client.delete("/api/v1/users/2/follow") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.body<JsonObject>()
            assertEquals("not following", body["message"]?.jsonPrimitive?.content)
        }

    // ═══════════════════════════════════════════════════════════
    // Mutual Follow Detection (via public profile)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `one-way follow status = 1`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { customerRepo.findById(2L) } returns customerRow(2, "bob", "Bob")
            coEvery { socialRepo.getFollowStatus(1L, 2L) } returns 1
            coEvery { socialRepo.getFollowingCount(2L) } returns 5
            coEvery { socialRepo.getFollowerCount(2L) } returns 3

            val client = jsonClient()
            val response =
                client.get("/api/v1/users/2") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val data = response.body<JsonObject>()["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(1, data["follow_status"]?.jsonPrimitive?.int)
        }

    @Test
    fun `mutual follow status = 2`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { customerRepo.findById(2L) } returns customerRow(2, "bob", "Bob")
            coEvery { socialRepo.getFollowStatus(1L, 2L) } returns 2
            coEvery { socialRepo.getFollowingCount(2L) } returns 5
            coEvery { socialRepo.getFollowerCount(2L) } returns 3

            val client = jsonClient()
            val response =
                client.get("/api/v1/users/2") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val data = response.body<JsonObject>()["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(2, data["follow_status"]?.jsonPrimitive?.int)
        }

    @Test
    fun `no follow status = 0`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { customerRepo.findById(2L) } returns customerRow(2, "bob", "Bob")
            coEvery { socialRepo.getFollowStatus(1L, 2L) } returns 0
            coEvery { socialRepo.getFollowingCount(2L) } returns 5
            coEvery { socialRepo.getFollowerCount(2L) } returns 3

            val client = jsonClient()
            val response =
                client.get("/api/v1/users/2") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val data = response.body<JsonObject>()["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(0, data["follow_status"]?.jsonPrimitive?.int)
        }

    @Test
    fun `unauthenticated viewer sees follow_status = 0`() =
        testApplication {
            setupApp()
            coEvery { customerRepo.findById(2L) } returns customerRow(2, "bob", "Bob")
            coEvery { socialRepo.getFollowStatus(null, 2L) } returns 0
            coEvery { socialRepo.getFollowingCount(2L) } returns 10
            coEvery { socialRepo.getFollowerCount(2L) } returns 5

            val client = jsonClient()
            val response = client.get("/api/v1/users/2")

            assertEquals(HttpStatusCode.OK, response.status)
            val data = response.body<JsonObject>()["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(0, data["follow_status"]?.jsonPrimitive?.int)
        }

    @Test
    fun `viewing own profile shows follow_status = 0`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            coEvery { customerRepo.findById(1L) } returns customerRow(1, "alice", "Alice")
            coEvery { socialRepo.getFollowStatus(1L, 1L) } returns 0
            coEvery { socialRepo.getFollowingCount(1L) } returns 3
            coEvery { socialRepo.getFollowerCount(1L) } returns 2

            val client = jsonClient()
            val response =
                client.get("/api/v1/users/1") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val data = response.body<JsonObject>()["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(0, data["follow_status"]?.jsonPrimitive?.int)
        }

    // ═══════════════════════════════════════════════════════════
    // Following/Followers Lists
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `get my following list`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            val items =
                listOf(
                    FollowUserRow(2L, "bob", "Bob", null, 1),
                    FollowUserRow(3L, "charlie", "Charlie", null, 2),
                    FollowUserRow(4L, "dave", null, null, 1),
                )
            coEvery { socialRepo.listFollowing(1L, 1, 20, 1L) } returns (items to 3L)

            val client = jsonClient()
            val response =
                client.get("/api/v1/users/me/following?page=1&page_size=20") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val data = response.body<JsonObject>()["data"]?.jsonObject
            assertNotNull(data)
            val itemsArray = data["items"]?.jsonArray
            assertNotNull(itemsArray)
            assertEquals(3, itemsArray.size)
            assertEquals(3L, data["total"]?.jsonPrimitive?.long)
        }

    @Test
    fun `get my followers list`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            val items =
                listOf(
                    FollowUserRow(3L, "charlie", "Charlie", null, 0),
                    FollowUserRow(4L, "dave", null, null, 1),
                )
            coEvery { socialRepo.listFollowers(1L, 1, 20, 1L) } returns (items to 2L)

            val client = jsonClient()
            val response =
                client.get("/api/v1/users/me/followers?page=1&page_size=20") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val data = response.body<JsonObject>()["data"]?.jsonObject
            assertNotNull(data)
            val itemsArray = data["items"]?.jsonArray
            assertNotNull(itemsArray)
            assertEquals(2, itemsArray.size)
        }

    @Test
    fun `following list shows mutual status`() =
        testApplication {
            setupApp()
            coEvery { tokenStore.isBlacklisted(any()) } returns false
            val items =
                listOf(
                    FollowUserRow(2L, "bob", "Bob", null, 2), // mutual
                    FollowUserRow(3L, "charlie", "Charlie", null, 1), // one-way
                )
            coEvery { socialRepo.listFollowing(1L, 1, 10, 1L) } returns (items to 2L)

            val client = jsonClient()
            val response =
                client.get("/api/v1/users/me/following") {
                    bearerAuth(aliceToken())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val data = response.body<JsonObject>()["data"]?.jsonObject
            assertNotNull(data)
            val itemsArray = data["items"]?.jsonArray!!
            assertEquals(2, itemsArray[0].jsonObject["follow_status"]?.jsonPrimitive?.int)
            assertEquals(1, itemsArray[1].jsonObject["follow_status"]?.jsonPrimitive?.int)
        }

    @Test
    fun `get another user's following list (public)`() =
        testApplication {
            setupApp()
            val items =
                (1..5).map {
                    FollowUserRow(it.toLong() + 10, "user$it", "User $it", null, 0)
                }
            coEvery { socialRepo.listFollowing(2L, 1, 10, null) } returns (items to 5L)

            val client = jsonClient()
            val response = client.get("/api/v1/users/2/following")

            assertEquals(HttpStatusCode.OK, response.status)
            val data = response.body<JsonObject>()["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(5, data["items"]?.jsonArray?.size)
        }

    @Test
    fun `get another user's followers list (public)`() =
        testApplication {
            setupApp()
            val items =
                (1..3).map {
                    FollowUserRow(it.toLong() + 10, "user$it", "User $it", null, 0)
                }
            coEvery { socialRepo.listFollowers(2L, 1, 10, null) } returns (items to 3L)

            val client = jsonClient()
            val response = client.get("/api/v1/users/2/followers")

            assertEquals(HttpStatusCode.OK, response.status)
            val data = response.body<JsonObject>()["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(3, data["items"]?.jsonArray?.size)
        }

    // ═══════════════════════════════════════════════════════════
    // Public Profile
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `get public profile - 200 with counts and follow_status`() =
        testApplication {
            setupApp()
            coEvery { customerRepo.findById(2L) } returns customerRow(2, "bob", "Bob")
            coEvery { socialRepo.getFollowingCount(2L) } returns 10
            coEvery { socialRepo.getFollowerCount(2L) } returns 5
            coEvery { socialRepo.getFollowStatus(null, 2L) } returns 0

            val client = jsonClient()
            val response = client.get("/api/v1/users/2")

            assertEquals(HttpStatusCode.OK, response.status)
            val data = response.body<JsonObject>()["data"]?.jsonObject
            assertNotNull(data)
            assertEquals(2, data["id"]?.jsonPrimitive?.int)
            assertEquals("bob", data["username"]?.jsonPrimitive?.content)
            assertEquals("Bob", data["name"]?.jsonPrimitive?.content)
            assertEquals("hello", data["signature"]?.jsonPrimitive?.content)
            assertEquals(false, data["is_vip"]?.jsonPrimitive?.boolean)
            assertEquals(10, data["following_count"]?.jsonPrimitive?.int)
            assertEquals(5, data["follower_count"]?.jsonPrimitive?.int)
            assertEquals(0, data["follow_status"]?.jsonPrimitive?.int)
        }

    @Test
    fun `get nonexistent user profile - 404`() =
        testApplication {
            setupApp()
            coEvery { customerRepo.findById(999999L) } returns null

            val client = jsonClient()
            val response = client.get("/api/v1/users/999999")

            assertEquals(HttpStatusCode.NotFound, response.status)
            val body = response.body<JsonObject>()
            assertEquals("user not found", body["message"]?.jsonPrimitive?.content)
        }
}
