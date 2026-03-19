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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import lab.gabon.model.JsonData
import lab.gabon.plugin.JsonPreserve
import lab.gabon.plugin.customerPrincipal
import lab.gabon.service.AvatarPresignResult
import lab.gabon.service.MyProfile
import lab.gabon.service.UserService

// -- Request DTOs --

@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val signature: String? = null,
)

@Serializable
data class AvatarPresignRequest(
    @JsonPreserve @SerialName("fileName") val fileName: String,
    @JsonPreserve @SerialName("contentType") val contentType: String,
)

@Serializable
data class AvatarConfirmRequest(
    @JsonPreserve @SerialName("avatarUrl") val avatarUrl: String,
)

// -- Response DTOs --

@Serializable
data class MyProfileDto(
    val id: Long,
    val username: String,
    val name: String?,
    val phone: String?,
    val email: String?,
    val avatarUrl: String?,
    val signature: String?,
    val isVip: Boolean,
    val diamondBalance: Long,
    val lastLoginAt: String?,
    val createdAt: String,
)

@Serializable
data class AvatarPresignDto(
    @JsonPreserve @SerialName("uploadUrl") val uploadUrl: String,
    @JsonPreserve @SerialName("avatarUrl") val avatarUrl: String,
)

// -- Route Registration --

fun Route.userRoutes(userService: UserService) {
    authenticate("customer") {
        route("/users/me") {
            get("/profile") {
                val principal = call.customerPrincipal()
                val profile = userService.getMyProfile(principal.customerId)
                call.respond(HttpStatusCode.OK, JsonData.ok(profile.toDto()))
            }

            put("/profile") {
                val principal = call.customerPrincipal()
                val req = call.receive<UpdateProfileRequest>()
                val profile =
                    userService.updateProfile(
                        customerId = principal.customerId,
                        name = req.name,
                        phone = req.phone,
                        email = req.email,
                        signature = req.signature,
                    )
                call.respond(HttpStatusCode.OK, JsonData.ok(profile.toDto()))
            }

            post("/avatar/upload-url") {
                val principal = call.customerPrincipal()
                val req = call.receive<AvatarPresignRequest>()
                val result =
                    userService.presignAvatarUpload(
                        customerId = principal.customerId,
                        fileName = req.fileName,
                        contentType = req.contentType,
                    )
                call.respond(HttpStatusCode.OK, JsonData.ok(result.toDto()))
            }

            post("/avatar/confirm") {
                val principal = call.customerPrincipal()
                val req = call.receive<AvatarConfirmRequest>()
                userService.confirmAvatarUpload(principal.customerId, req.avatarUrl)
                call.respond(HttpStatusCode.OK, JsonData.ok("avatar updated"))
            }
        }
    }
}

// -- Extension mappers --

private fun MyProfile.toDto(): MyProfileDto =
    MyProfileDto(
        id = id,
        username = username,
        name = name,
        phone = phone,
        email = email,
        avatarUrl = avatarUrl,
        signature = signature,
        isVip = isVip,
        diamondBalance = diamondBalance,
        lastLoginAt = lastLoginAt,
        createdAt = createdAt,
    )

private fun AvatarPresignResult.toDto(): AvatarPresignDto =
    AvatarPresignDto(
        uploadUrl = uploadUrl,
        avatarUrl = avatarUrl,
    )
