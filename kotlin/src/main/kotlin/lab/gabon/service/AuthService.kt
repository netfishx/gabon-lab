package lab.gabon.service

import at.favre.lib.crypto.bcrypt.BCrypt
import lab.gabon.model.AppError
import lab.gabon.model.AppException
import lab.gabon.repository.CustomerRepo
import lab.gabon.repository.CustomerRow

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
)

data class CustomerProfile(
    val id: Long,
    val username: String,
    val name: String?,
    val phone: String?,
    val email: String?,
    val avatarUrl: String?,
    val signature: String?,
    val isVip: Boolean,
    val diamondBalance: Long,
)

class AuthService(
    private val customerRepo: CustomerRepo,
    private val jwtService: JwtService,
    private val tokenStore: RedisTokenStore,
) {
    suspend fun register(
        username: String,
        password: String,
    ): TokenResponse {
        validateInput(username, password)

        val existing = customerRepo.findByUsername(username)
        if (existing != null) {
            throw AppException(AppError.UsernameExists())
        }

        val hash = hashPassword(password)
        val customerId = customerRepo.create(username, hash)

        val tokenPair = jwtService.generateCustomerTokens(customerId)
        tokenStore.setFamily(
            familyId = tokenPair.familyId,
            userId = customerId,
            currentJti = tokenPair.refreshJti,
            ttlSeconds = jwtService.config.customerRefreshTtl.inWholeSeconds,
        )

        return TokenResponse(tokenPair.accessToken, tokenPair.refreshToken)
    }

    suspend fun login(
        username: String,
        password: String,
    ): TokenResponse {
        val customer =
            customerRepo.findByUsername(username)
                ?: throw AppException(AppError.InvalidCredentials())

        if (!verifyPassword(password, customer.passwordHash)) {
            throw AppException(AppError.InvalidCredentials())
        }

        customerRepo.updateLastLogin(customer.id)

        val tokenPair = jwtService.generateCustomerTokens(customer.id)
        tokenStore.setFamily(
            familyId = tokenPair.familyId,
            userId = customer.id,
            currentJti = tokenPair.refreshJti,
            ttlSeconds = jwtService.config.customerRefreshTtl.inWholeSeconds,
        )

        return TokenResponse(tokenPair.accessToken, tokenPair.refreshToken)
    }

    suspend fun refresh(refreshTokenStr: String): TokenResponse {
        val claims = jwtService.parseCustomerToken(refreshTokenStr)

        if (claims.tokenType != "refresh") {
            throw AppException(AppError.TokenInvalid("not a refresh token"))
        }

        // Generate new token pair first, then atomically CAS the family
        val newTokenPair = jwtService.generateCustomerTokens(claims.userId)

        val casResult =
            tokenStore.casFamily(
                familyId = claims.familyId,
                expectedJti = claims.jti,
                newJti = newTokenPair.refreshJti,
            )

        return when (casResult) {
            is CasResult.Success ->
                TokenResponse(newTokenPair.accessToken, newTokenPair.refreshToken)
            is CasResult.Missing ->
                throw AppException(AppError.TokenInvalid("token family expired or revoked"))
            is CasResult.Conflict ->
                throw AppException(AppError.TokenInvalid("token reuse detected, family revoked"))
        }
    }

    @Suppress("UnusedParameter")
    suspend fun logout(
        customerId: Long,
        jti: String,
        familyId: String,
    ) {
        val remainingSeconds = jwtService.config.customerAccessTtl.inWholeSeconds
        tokenStore.setBlacklist(jti, remainingSeconds)
        tokenStore.deleteFamily(familyId)
    }

    suspend fun changePassword(
        customerId: Long,
        oldPassword: String,
        newPassword: String,
    ) {
        val customer =
            customerRepo.findById(customerId)
                ?: throw AppException(AppError.NotFound("customer not found"))

        if (!verifyPassword(oldPassword, customer.passwordHash)) {
            throw AppException(AppError.PasswordMismatch())
        }

        val newHash = hashPassword(newPassword)
        customerRepo.updatePassword(customerId, newHash)
    }

    suspend fun getMe(customerId: Long): CustomerProfile {
        val customer =
            customerRepo.findById(customerId)
                ?: throw AppException(AppError.NotFound("customer not found"))

        return customer.toProfile()
    }

    private fun validateInput(
        username: String,
        password: String,
    ) {
        if (username.length < MIN_USERNAME_LENGTH) {
            throw AppException(AppError.BadRequest("username must be at least $MIN_USERNAME_LENGTH characters"))
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw AppException(AppError.BadRequest("password must be at least $MIN_PASSWORD_LENGTH characters"))
        }
    }

    private fun hashPassword(password: String): String =
        BCrypt
            .withDefaults()
            .hashToString(BCRYPT_COST, password.toCharArray())

    private fun verifyPassword(
        password: String,
        hash: String,
    ): Boolean = BCrypt.verifyer().verify(password.toCharArray(), hash).verified

    private fun CustomerRow.toProfile(): CustomerProfile =
        CustomerProfile(
            id = id,
            username = username,
            name = name,
            phone = phone,
            email = email,
            avatarUrl = avatarUrl,
            signature = signature,
            isVip = isVip,
            diamondBalance = diamondBalance,
        )

    companion object {
        private const val BCRYPT_COST = 10
        private const val MIN_USERNAME_LENGTH = 3
        private const val MIN_PASSWORD_LENGTH = 6
    }
}
