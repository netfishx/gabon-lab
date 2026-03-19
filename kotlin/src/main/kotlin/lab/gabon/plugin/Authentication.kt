package lab.gabon.plugin

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import lab.gabon.model.AppError
import lab.gabon.model.AppException
import lab.gabon.model.JsonData
import lab.gabon.service.JwtService
import lab.gabon.service.RedisTokenStore

data class CustomerPrincipal(
    val customerId: Long,
    val jti: String,
    val familyId: String,
)

data class AdminPrincipal(
    val adminId: Long,
    val role: String,
    val jti: String,
    val familyId: String,
)

fun Application.configureAuthentication(
    jwtService: JwtService,
    tokenStore: RedisTokenStore,
) {
    val config = jwtService.config

    install(Authentication) {
        jwt("customer") {
            realm = "gabon-customer"
            verifier(
                JWT
                    .require(Algorithm.HMAC256(config.customerSecret))
                    .withIssuer(JwtService.CUSTOMER_ISSUER)
                    .withAudience(JwtService.CUSTOMER_AUDIENCE)
                    .build(),
            )
            validate { credential ->
                val payload = credential.payload
                val tokenType = payload.getClaim("token_type").asString()
                if (tokenType != "access") return@validate null

                val jti = payload.id ?: return@validate null
                if (tokenStore.isBlacklisted(jti)) return@validate null

                val customerId = payload.subject?.toLongOrNull() ?: return@validate null
                val familyId = payload.getClaim("family_id").asString() ?: return@validate null

                CustomerPrincipal(customerId, jti, familyId)
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    JsonData.error(AppError.TokenInvalid()),
                )
            }
        }

        jwt("admin") {
            realm = "gabon-admin"
            verifier(
                JWT
                    .require(Algorithm.HMAC256(config.adminSecret))
                    .withIssuer(JwtService.ADMIN_ISSUER)
                    .withAudience(JwtService.ADMIN_AUDIENCE)
                    .build(),
            )
            validate { credential ->
                val payload = credential.payload
                val tokenType = payload.getClaim("token_type").asString()
                if (tokenType != "access") return@validate null

                val jti = payload.id ?: return@validate null
                if (tokenStore.isBlacklisted(jti)) return@validate null

                val adminId = payload.subject?.toLongOrNull() ?: return@validate null
                val role = payload.getClaim("role").asString() ?: return@validate null
                val familyId = payload.getClaim("family_id").asString() ?: return@validate null

                AdminPrincipal(adminId, role, jti, familyId)
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    JsonData.error(AppError.TokenInvalid()),
                )
            }
        }
    }
}

/** Extract customer principal from the call, throw Unauthorized if missing. */
fun ApplicationCall.customerPrincipal(): CustomerPrincipal =
    principal<CustomerPrincipal>()
        ?: throw AppException(AppError.TokenInvalid("missing customer principal"))

/** Extract admin principal from the call, throw Unauthorized if missing. */
fun ApplicationCall.adminPrincipal(): AdminPrincipal =
    principal<AdminPrincipal>()
        ?: throw AppException(AppError.TokenInvalid("missing admin principal"))
