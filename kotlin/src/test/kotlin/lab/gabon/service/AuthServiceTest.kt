package lab.gabon.service

import at.favre.lib.crypto.bcrypt.BCrypt
import io.mockk.*
import kotlinx.datetime.Clock
import lab.gabon.config.JwtConfig
import lab.gabon.model.AppError
import lab.gabon.model.AppException
import lab.gabon.repository.CustomerRepo
import lab.gabon.repository.CustomerRow
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Unit tests for AuthService business logic.
 * Mocks CustomerRepo and RedisTokenStore to isolate service logic.
 */
class AuthServiceTest {

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
    private val customerRepo = mockk<CustomerRepo>()
    private val tokenStore = mockk<RedisTokenStore>()
    private val authService = AuthService(customerRepo, jwtService, tokenStore)

    private val secret123Hash: String =
        BCrypt.withDefaults().hashToString(4, "secret123".toCharArray())

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

    // ═══════════════════════════════════════════════════════════
    // Register
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `register - success creates customer and returns tokens`() = runTest {
        coEvery { customerRepo.findByUsername("alice") } returns null
        coEvery { customerRepo.create("alice", any()) } returns 1L
        coEvery { tokenStore.setFamily(any(), any(), any(), any()) } just Runs

        val result = authService.register("alice", "secret123")

        assertNotNull(result.accessToken)
        assertNotNull(result.refreshToken)
        coVerify { customerRepo.create("alice", any()) }
        coVerify { tokenStore.setFamily(any(), 1L, any(), any()) }
    }

    @Test
    fun `register - duplicate username throws UsernameExists`() = runTest {
        coEvery { customerRepo.findByUsername("bob") } returns aliceRow(username = "bob")

        val ex = assertFailsWith<AppException> {
            authService.register("bob", "secret123")
        }
        assertIs<AppError.UsernameExists>(ex.error)
    }

    @Test
    fun `register - username too short throws BadRequest`() = runTest {
        val ex = assertFailsWith<AppException> {
            authService.register("ab", "secret123")
        }
        assertIs<AppError.BadRequest>(ex.error)
        assertTrue(ex.error.message.contains("3"))
    }

    @Test
    fun `register - password too short throws BadRequest`() = runTest {
        val ex = assertFailsWith<AppException> {
            authService.register("alice", "12345")
        }
        assertIs<AppError.BadRequest>(ex.error)
        assertTrue(ex.error.message.contains("6"))
    }

    // ═══════════════════════════════════════════════════════════
    // Login
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `login - success returns tokens and updates last login`() = runTest {
        coEvery { customerRepo.findByUsername("alice") } returns aliceRow()
        coEvery { customerRepo.updateLastLogin(1L) } just Runs
        coEvery { tokenStore.setFamily(any(), any(), any(), any()) } just Runs

        val result = authService.login("alice", "secret123")

        assertNotNull(result.accessToken)
        assertNotNull(result.refreshToken)
        coVerify { customerRepo.updateLastLogin(1L) }
    }

    @Test
    fun `login - wrong password throws InvalidCredentials`() = runTest {
        coEvery { customerRepo.findByUsername("alice") } returns aliceRow()

        val ex = assertFailsWith<AppException> {
            authService.login("alice", "wrongpass")
        }
        assertIs<AppError.InvalidCredentials>(ex.error)
    }

    @Test
    fun `login - nonexistent user throws InvalidCredentials`() = runTest {
        coEvery { customerRepo.findByUsername("nonexistent") } returns null

        val ex = assertFailsWith<AppException> {
            authService.login("nonexistent", "secret123")
        }
        assertIs<AppError.InvalidCredentials>(ex.error)
    }

    // ═══════════════════════════════════════════════════════════
    // Refresh
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `refresh - success with valid refresh token`() = runTest {
        val tokenPair = jwtService.generateCustomerTokens(1L)

        coEvery {
            tokenStore.casFamily(tokenPair.familyId, tokenPair.refreshJti, any())
        } returns CasResult.Success(userId = 1L)

        val result = authService.refresh(tokenPair.refreshToken)

        assertNotNull(result.accessToken)
        assertNotNull(result.refreshToken)
        // New tokens differ from old
        assertNotEquals(tokenPair.accessToken, result.accessToken)
        assertNotEquals(tokenPair.refreshToken, result.refreshToken)
    }

    @Test
    fun `refresh - access token rejected as not a refresh token`() = runTest {
        val tokenPair = jwtService.generateCustomerTokens(1L)

        val ex = assertFailsWith<AppException> {
            authService.refresh(tokenPair.accessToken)
        }
        assertIs<AppError.TokenInvalid>(ex.error)
        assertEquals("not a refresh token", ex.error.message)
    }

    @Test
    fun `refresh - missing family throws token invalid`() = runTest {
        val tokenPair = jwtService.generateCustomerTokens(1L)

        coEvery {
            tokenStore.casFamily(tokenPair.familyId, tokenPair.refreshJti, any())
        } returns CasResult.Missing

        val ex = assertFailsWith<AppException> {
            authService.refresh(tokenPair.refreshToken)
        }
        assertIs<AppError.TokenInvalid>(ex.error)
        assertEquals("token family expired or revoked", ex.error.message)
    }

    @Test
    fun `refresh - replay detected (CAS conflict) throws and revokes family`() = runTest {
        val tokenPair = jwtService.generateCustomerTokens(1L)

        coEvery {
            tokenStore.casFamily(tokenPair.familyId, tokenPair.refreshJti, any())
        } returns CasResult.Conflict

        val ex = assertFailsWith<AppException> {
            authService.refresh(tokenPair.refreshToken)
        }
        assertIs<AppError.TokenInvalid>(ex.error)
        assertEquals("token reuse detected, family revoked", ex.error.message)
    }

    // ═══════════════════════════════════════════════════════════
    // Logout
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `logout - blacklists JTI and deletes family`() = runTest {
        coEvery { tokenStore.setBlacklist(any(), any()) } just Runs
        coEvery { tokenStore.deleteFamily(any()) } just Runs

        authService.logout(1L, "test-jti", "test-family")

        coVerify { tokenStore.setBlacklist("test-jti", 15.minutes.inWholeSeconds) }
        coVerify { tokenStore.deleteFamily("test-family") }
    }

    // ═══════════════════════════════════════════════════════════
    // Change Password
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `changePassword - success updates hash`() = runTest {
        coEvery { customerRepo.findById(1L) } returns aliceRow()
        coEvery { customerRepo.updatePassword(1L, any()) } just Runs

        authService.changePassword(1L, "secret123", "newsecret456")

        coVerify { customerRepo.updatePassword(1L, any()) }
    }

    @Test
    fun `changePassword - wrong old password throws PasswordMismatch`() = runTest {
        coEvery { customerRepo.findById(1L) } returns aliceRow()

        val ex = assertFailsWith<AppException> {
            authService.changePassword(1L, "wrongold", "newsecret456")
        }
        assertIs<AppError.PasswordMismatch>(ex.error)
    }

    @Test
    fun `changePassword - nonexistent customer throws NotFound`() = runTest {
        coEvery { customerRepo.findById(999L) } returns null

        val ex = assertFailsWith<AppException> {
            authService.changePassword(999L, "old", "new123")
        }
        assertIs<AppError.NotFound>(ex.error)
    }

    // ═══════════════════════════════════════════════════════════
    // GetMe
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `getMe - returns profile for existing customer`() = runTest {
        coEvery { customerRepo.findById(1L) } returns aliceRow()

        val profile = authService.getMe(1L)

        assertEquals(1L, profile.id)
        assertEquals("alice", profile.username)
        assertEquals("Alice", profile.name)
        assertEquals(false, profile.isVip)
        assertEquals(100L, profile.diamondBalance)
    }

    @Test
    fun `getMe - nonexistent customer throws NotFound`() = runTest {
        coEvery { customerRepo.findById(999L) } returns null

        val ex = assertFailsWith<AppException> {
            authService.getMe(999L)
        }
        assertIs<AppError.NotFound>(ex.error)
    }

    // ═══════════════════════════════════════════════════════════
    // JWT Token Properties
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `generated tokens have correct issuer and audience`() {
        val tokenPair = jwtService.generateCustomerTokens(1L)

        val accessClaims = jwtService.parseCustomerToken(tokenPair.accessToken)
        assertEquals(1L, accessClaims.userId)
        assertEquals("access", accessClaims.tokenType)
        assertEquals("kid-test-001", accessClaims.kid)

        val refreshClaims = jwtService.parseCustomerToken(tokenPair.refreshToken)
        assertEquals(1L, refreshClaims.userId)
        assertEquals("refresh", refreshClaims.tokenType)
        assertEquals(tokenPair.familyId, refreshClaims.familyId)
    }

    @Test
    fun `admin token cannot be parsed as customer token`() {
        val adminTokenPair = jwtService.generateAdminTokens(1L, "superadmin")

        assertFailsWith<AppException> {
            jwtService.parseCustomerToken(adminTokenPair.accessToken)
        }
    }
}
