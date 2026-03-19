package lab.gabon.route

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import lab.gabon.model.JsonData
import lab.gabon.model.Paginated
import lab.gabon.plugin.CustomerPrincipal
import lab.gabon.plugin.customerPrincipal
import lab.gabon.repository.CustomerRepo
import lab.gabon.repository.FollowUserRow
import lab.gabon.service.SocialService

// ── Response DTOs ───────────────────────────────────────────

@Serializable
data class FollowUserDto(
    val userId: Long,
    val username: String,
    val name: String?,
    val avatarUrl: String?,
    val followStatus: Int,
)

@Serializable
data class PublicProfileDto(
    val id: Long,
    val username: String,
    val name: String?,
    val avatarUrl: String?,
    val signature: String?,
    val isVip: Boolean,
    val followingCount: Long,
    val followerCount: Long,
    val followStatus: Int,
)

// ── Route Registration ──────────────────────────────────────

fun Route.socialRoutes(socialService: SocialService, customerRepo: CustomerRepo) {

    // ── Authenticated routes ────────────────────────────────
    authenticate("customer") {
        // POST /users/{userId}/follow
        post("/users/{userId}/follow") {
            val principal = call.customerPrincipal()
            val targetId = call.parameters["userId"]?.toLongOrNull()
                ?: throw lab.gabon.model.AppException(lab.gabon.model.AppError.BadRequest("invalid user id"))

            socialService.follow(principal.customerId, targetId)
            call.respond(HttpStatusCode.OK, JsonData.ok("followed"))
        }

        // DELETE /users/{userId}/follow
        delete("/users/{userId}/follow") {
            val principal = call.customerPrincipal()
            val targetId = call.parameters["userId"]?.toLongOrNull()
                ?: throw lab.gabon.model.AppException(lab.gabon.model.AppError.BadRequest("invalid user id"))

            socialService.unfollow(principal.customerId, targetId)
            call.respond(HttpStatusCode.OK, JsonData.ok("unfollowed"))
        }

        // GET /users/me/following
        get("/users/me/following") {
            val principal = call.customerPrincipal()
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.parameters["page_size"]?.toIntOrNull() ?: 10

            val (items, total) = socialService.getFollowing(
                userId = principal.customerId,
                page = page,
                pageSize = pageSize,
                viewerId = principal.customerId,
            )

            call.respond(
                HttpStatusCode.OK,
                JsonData.ok(Paginated(items.map { it.toDto() }, total, page, pageSize)),
            )
        }

        // GET /users/me/followers
        get("/users/me/followers") {
            val principal = call.customerPrincipal()
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.parameters["page_size"]?.toIntOrNull() ?: 10

            val (items, total) = socialService.getFollowers(
                userId = principal.customerId,
                page = page,
                pageSize = pageSize,
                viewerId = principal.customerId,
            )

            call.respond(
                HttpStatusCode.OK,
                JsonData.ok(Paginated(items.map { it.toDto() }, total, page, pageSize)),
            )
        }
    }

    // ── Public routes (optional auth for follow_status) ─────
    authenticate("customer", optional = true) {
        // GET /users/{userId} — public profile
        get("/users/{userId}") {
            val targetId = call.parameters["userId"]?.toLongOrNull()
                ?: throw lab.gabon.model.AppException(lab.gabon.model.AppError.BadRequest("invalid user id"))

            val customer = customerRepo.findById(targetId)
                ?: throw lab.gabon.model.AppException(lab.gabon.model.AppError.NotFound("user not found"))

            val viewerId = call.principal<CustomerPrincipal>()?.customerId

            val followingCount = socialService.getFollowingCount(targetId)
            val followerCount = socialService.getFollowerCount(targetId)
            val followStatus = socialService.getFollowStatus(viewerId, targetId)

            call.respond(
                HttpStatusCode.OK,
                JsonData.ok(
                    PublicProfileDto(
                        id = customer.id,
                        username = customer.username,
                        name = customer.name,
                        avatarUrl = customer.avatarUrl,
                        signature = customer.signature,
                        isVip = customer.isVip,
                        followingCount = followingCount,
                        followerCount = followerCount,
                        followStatus = followStatus,
                    )
                ),
            )
        }

        // GET /users/{userId}/following
        get("/users/{userId}/following") {
            val targetId = call.parameters["userId"]?.toLongOrNull()
                ?: throw lab.gabon.model.AppException(lab.gabon.model.AppError.BadRequest("invalid user id"))

            val viewerId = call.principal<CustomerPrincipal>()?.customerId
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.parameters["page_size"]?.toIntOrNull() ?: 10

            val (items, total) = socialService.getFollowing(
                userId = targetId,
                page = page,
                pageSize = pageSize,
                viewerId = viewerId,
            )

            call.respond(
                HttpStatusCode.OK,
                JsonData.ok(Paginated(items.map { it.toDto() }, total, page, pageSize)),
            )
        }

        // GET /users/{userId}/followers
        get("/users/{userId}/followers") {
            val targetId = call.parameters["userId"]?.toLongOrNull()
                ?: throw lab.gabon.model.AppException(lab.gabon.model.AppError.BadRequest("invalid user id"))

            val viewerId = call.principal<CustomerPrincipal>()?.customerId
            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.parameters["page_size"]?.toIntOrNull() ?: 10

            val (items, total) = socialService.getFollowers(
                userId = targetId,
                page = page,
                pageSize = pageSize,
                viewerId = viewerId,
            )

            call.respond(
                HttpStatusCode.OK,
                JsonData.ok(Paginated(items.map { it.toDto() }, total, page, pageSize)),
            )
        }
    }
}

// ── Extension mappers ───────────────────────────────────────

private fun FollowUserRow.toDto(): FollowUserDto = FollowUserDto(
    userId = userId,
    username = username,
    name = name,
    avatarUrl = avatarUrl,
    followStatus = followStatus,
)
