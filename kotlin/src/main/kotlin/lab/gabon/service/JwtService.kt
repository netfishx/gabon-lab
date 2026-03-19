package lab.gabon.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException
import lab.gabon.config.JwtConfig
import lab.gabon.model.AppError
import lab.gabon.model.AppException
import java.util.Date
import java.util.UUID

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val familyId: String,
    val accessJti: String,
    val refreshJti: String,
)

data class TokenClaims(
    val userId: Long,
    val jti: String,
    val tokenType: String,
    val familyId: String,
    val kid: String,
    val role: String?,
)

class JwtService(
    val config: JwtConfig,
) {
    private val customerAlgorithm = Algorithm.HMAC256(config.customerSecret)
    private val adminAlgorithm = Algorithm.HMAC256(config.adminSecret)

    private val customerVerifier =
        JWT
            .require(customerAlgorithm)
            .withIssuer(CUSTOMER_ISSUER)
            .withAudience(CUSTOMER_AUDIENCE)
            .build()

    private val adminVerifier =
        JWT
            .require(adminAlgorithm)
            .withIssuer(ADMIN_ISSUER)
            .withAudience(ADMIN_AUDIENCE)
            .build()

    fun generateCustomerTokens(
        customerId: Long,
        existingFamilyId: String? = null,
    ): TokenPair {
        val familyId = existingFamilyId ?: UUID.randomUUID().toString()
        val accessJti = UUID.randomUUID().toString()
        val refreshJti = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val accessToken =
            JWT
                .create()
                .withIssuer(CUSTOMER_ISSUER)
                .withAudience(CUSTOMER_AUDIENCE)
                .withKeyId(config.currentKid)
                .withSubject(customerId.toString())
                .withJWTId(accessJti)
                .withClaim("token_type", "access")
                .withClaim("family_id", familyId)
                .withIssuedAt(Date(now))
                .withExpiresAt(Date(now + config.customerAccessTtl.inWholeMilliseconds))
                .sign(customerAlgorithm)

        val refreshToken =
            JWT
                .create()
                .withIssuer(CUSTOMER_ISSUER)
                .withAudience(CUSTOMER_AUDIENCE)
                .withKeyId(config.currentKid)
                .withSubject(customerId.toString())
                .withJWTId(refreshJti)
                .withClaim("token_type", "refresh")
                .withClaim("family_id", familyId)
                .withIssuedAt(Date(now))
                .withExpiresAt(Date(now + config.customerRefreshTtl.inWholeMilliseconds))
                .sign(customerAlgorithm)

        return TokenPair(accessToken, refreshToken, familyId, accessJti, refreshJti)
    }

    fun generateAdminTokens(
        adminId: Long,
        role: String,
        existingFamilyId: String? = null,
    ): TokenPair {
        val familyId = existingFamilyId ?: UUID.randomUUID().toString()
        val accessJti = UUID.randomUUID().toString()
        val refreshJti = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val accessToken =
            JWT
                .create()
                .withIssuer(ADMIN_ISSUER)
                .withAudience(ADMIN_AUDIENCE)
                .withKeyId(config.currentKid)
                .withSubject(adminId.toString())
                .withJWTId(accessJti)
                .withClaim("token_type", "access")
                .withClaim("family_id", familyId)
                .withClaim("role", role)
                .withIssuedAt(Date(now))
                .withExpiresAt(Date(now + config.adminAccessTtl.inWholeMilliseconds))
                .sign(adminAlgorithm)

        val refreshToken =
            JWT
                .create()
                .withIssuer(ADMIN_ISSUER)
                .withAudience(ADMIN_AUDIENCE)
                .withKeyId(config.currentKid)
                .withSubject(adminId.toString())
                .withJWTId(refreshJti)
                .withClaim("token_type", "refresh")
                .withClaim("family_id", familyId)
                .withClaim("role", role)
                .withIssuedAt(Date(now))
                .withExpiresAt(Date(now + config.adminRefreshTtl.inWholeMilliseconds))
                .sign(adminAlgorithm)

        return TokenPair(accessToken, refreshToken, familyId, accessJti, refreshJti)
    }

    fun parseCustomerToken(token: String): TokenClaims = parseToken(token, customerVerifier)

    fun parseAdminToken(token: String): TokenClaims = parseToken(token, adminVerifier)

    @Suppress("ThrowsCount")
    private fun parseToken(
        token: String,
        verifier: com.auth0.jwt.JWTVerifier,
    ): TokenClaims {
        val payload =
            try {
                verifier.verify(token)
            } catch (
                @Suppress("SwallowedException") e: TokenExpiredException,
            ) {
                throw AppException(AppError.TokenExpired())
            } catch (_: JWTVerificationException) {
                throw AppException(AppError.TokenInvalid())
            }

        return TokenClaims(
            userId =
                payload.subject?.toLongOrNull()
                    ?: throw AppException(AppError.TokenInvalid("missing subject")),
            jti =
                payload.id
                    ?: throw AppException(AppError.TokenInvalid("missing jti")),
            tokenType =
                payload.getClaim("token_type").asString()
                    ?: throw AppException(AppError.TokenInvalid("missing token_type")),
            familyId =
                payload.getClaim("family_id").asString()
                    ?: throw AppException(AppError.TokenInvalid("missing family_id")),
            kid =
                payload.keyId
                    ?: throw AppException(AppError.TokenInvalid("missing kid")),
            role = payload.getClaim("role").asString(),
        )
    }

    companion object {
        const val CUSTOMER_ISSUER = "gabon-service"
        const val CUSTOMER_AUDIENCE = "customer"
        const val ADMIN_ISSUER = "gabon-admin"
        const val ADMIN_AUDIENCE = "admin"
    }
}
