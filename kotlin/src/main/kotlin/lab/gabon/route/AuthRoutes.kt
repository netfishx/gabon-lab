package lab.gabon.route

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import lab.gabon.model.JsonData
import lab.gabon.plugin.customerPrincipal
import lab.gabon.service.AuthService
import lab.gabon.service.CustomerProfile
import lab.gabon.service.TokenResponse

// ── Request DTOs ────────────────────────────────────────────

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String,
)

// ── Response DTOs ───────────────────────────────────────────

@Serializable
data class TokenResponseDto(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class CustomerProfileDto(
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

// ── Route Registration ──────────────────────────────────────

fun Route.authRoutes(authService: AuthService) {
    route("/auth") {
        // Public routes (no auth required)
        post("/register") {
            val req = call.receive<RegisterRequest>()
            val result = authService.register(req.username, req.password)
            call.respond(HttpStatusCode.Created, JsonData.ok(result.toDto()))
        }

        post("/login") {
            val req = call.receive<LoginRequest>()
            val result = authService.login(req.username, req.password)
            call.respond(HttpStatusCode.OK, JsonData.ok(result.toDto()))
        }

        post("/refresh") {
            val req = call.receive<RefreshRequest>()
            val result = authService.refresh(req.refreshToken)
            call.respond(HttpStatusCode.OK, JsonData.ok(result.toDto()))
        }

        // Protected routes (require customer auth)
        authenticate("customer") {
            get("/me") {
                val principal = call.customerPrincipal()
                val profile = authService.getMe(principal.customerId)
                call.respond(HttpStatusCode.OK, JsonData.ok(profile.toDto()))
            }

            put("/password") {
                val principal = call.customerPrincipal()
                val req = call.receive<ChangePasswordRequest>()
                authService.changePassword(principal.customerId, req.oldPassword, req.newPassword)
                call.respond(HttpStatusCode.OK, JsonData.ok("password changed"))
            }

            post("/logout") {
                val principal = call.customerPrincipal()
                authService.logout(principal.customerId, principal.jti, principal.familyId)
                call.respond(HttpStatusCode.OK, JsonData.ok("logged out"))
            }
        }
    }
}

// ── Extension mappers ───────────────────────────────────────

private fun TokenResponse.toDto(): TokenResponseDto =
    TokenResponseDto(
        accessToken = accessToken,
        refreshToken = refreshToken,
    )

private fun CustomerProfile.toDto(): CustomerProfileDto =
    CustomerProfileDto(
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
