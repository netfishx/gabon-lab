package lab.gabon.route

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import lab.gabon.model.JsonData
import lab.gabon.model.Paginated
import lab.gabon.plugin.adminPrincipal
import lab.gabon.repository.AdminUserRow
import lab.gabon.repository.AdminVideoListRow
import lab.gabon.repository.AdminVideoRow
import lab.gabon.repository.CustomerRow
import lab.gabon.service.AdminService
import lab.gabon.service.TokenResponse

// ── Request DTOs ────────────────────────────────────────────

@Serializable
data class AdminLoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class AdminRefreshRequest(
    val refreshToken: String,
)

@Serializable
data class CreateAdminRequest(
    val username: String,
    val password: String,
    val role: Short,
    val fullName: String? = null,
)

@Serializable
data class UpdateAdminRequest(
    val role: Short? = null,
    val fullName: String? = null,
    val phone: String? = null,
    val status: Short? = null,
)

@Serializable
data class AdminChangePasswordRequest(
    val newPassword: String,
)

@Serializable
data class ReviewVideoRequest(
    val status: Short,
    val reviewNotes: String? = null,
)

@Serializable
data class ResetCustomerPasswordRequest(
    val newPassword: String,
)

// ── Response DTOs ───────────────────────────────────────────

@Serializable
data class AdminTokenResponseDto(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class AdminUserDto(
    val id: Long,
    val username: String,
    val role: Short,
    val fullName: String?,
    val phone: String?,
    val avatarUrl: String?,
    val status: Short,
    val lastLoginAt: String?,
    val createdAt: String,
)

@Serializable
data class AdminVideoListItemDto(
    val id: Long,
    val customerId: Long,
    val title: String?,
    val fileName: String,
    val fileUrl: String,
    val thumbnailUrl: String?,
    val mimeType: String,
    val status: Short,
    val reviewNotes: String?,
    val reviewedBy: Long?,
    val totalClicks: Long,
    val validClicks: Long,
    val likeCount: Long,
    val createdAt: String,
    val authorName: String?,
    val authorAvatar: String?,
    val authorIsVip: Boolean,
)

@Serializable
data class AdminVideoDetailDto(
    val id: Long,
    val customerId: Long,
    val title: String?,
    val description: String?,
    val fileName: String,
    val fileSize: Long,
    val fileUrl: String,
    val thumbnailUrl: String?,
    val previewGifUrl: String?,
    val mimeType: String,
    val duration: Int?,
    val width: Int?,
    val height: Int?,
    val status: Short,
    val reviewNotes: String?,
    val reviewedBy: Long?,
    val reviewedAt: String?,
    val totalClicks: Long,
    val validClicks: Long,
    val likeCount: Long,
    val createdAt: String,
    val updatedAt: String,
    val authorName: String?,
    val authorAvatar: String?,
    val authorIsVip: Boolean,
)

@Serializable
data class CustomerListDto(
    val id: Long,
    val username: String,
    val name: String?,
    val phone: String?,
    val email: String?,
    val avatarUrl: String?,
    val isVip: Boolean,
    val diamondBalance: Long,
    val createdAt: String,
)

// ── Route Registration ──────────────────────────────────────

fun Route.adminRoutes(adminService: AdminService) {
    route("/admin/v1") {
        // ── Public admin auth routes ──────────────────────
        route("/auth") {
            post("/login") {
                val req = call.receive<AdminLoginRequest>()
                val result = adminService.login(req.username, req.password)
                call.respond(HttpStatusCode.OK, JsonData.ok(result.toAdminDto()))
            }

            post("/refresh") {
                val req = call.receive<AdminRefreshRequest>()
                val result = adminService.refresh(req.refreshToken)
                call.respond(HttpStatusCode.OK, JsonData.ok(result.toAdminDto()))
            }
        }

        // ── Protected admin routes ────────────────────────
        authenticate("admin") {
            // ── Auth ──────────────────────────────────────
            route("/auth") {
                post("/logout") {
                    val principal = call.adminPrincipal()
                    adminService.logout(principal.adminId, principal.jti, principal.familyId)
                    call.respond(HttpStatusCode.OK, JsonData.ok("logged out"))
                }

                get("/me") {
                    val principal = call.adminPrincipal()
                    val admin = adminService.getMe(principal.adminId)
                    call.respond(HttpStatusCode.OK, JsonData.ok(admin.toDto()))
                }
            }

            // ── Videos ────────────────────────────────────
            route("/videos") {
                get {
                    val page = call.queryParameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.queryParameters["page_size"]?.toIntOrNull() ?: 20
                    val status = call.queryParameters["status"]?.toShortOrNull()
                    val authorName = call.queryParameters["author_name"]
                    val startDate = call.queryParameters["start_date"]
                    val endDate = call.queryParameters["end_date"]
                    val isVip = call.queryParameters["is_vip"]?.toBooleanStrictOrNull()

                    val (items, total) = adminService.listVideosAdmin(
                        page, pageSize, status, authorName, startDate, endDate, isVip,
                    )
                    call.respond(
                        HttpStatusCode.OK,
                        JsonData.ok(Paginated(items.map { it.toDto() }, total, page, pageSize)),
                    )
                }

                get("/{id}") {
                    val videoId = call.pathParameters["id"]?.toLongOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            JsonData.error(400, "invalid video id"),
                        )

                    val detail = adminService.getVideoDetailAdmin(videoId)
                    call.respond(HttpStatusCode.OK, JsonData.ok(detail.toDto()))
                }

                post("/{id}/review") {
                    val principal = call.adminPrincipal()
                    val videoId = call.pathParameters["id"]?.toLongOrNull()
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            JsonData.error(400, "invalid video id"),
                        )

                    val req = call.receive<ReviewVideoRequest>()
                    adminService.reviewVideo(videoId, principal.adminId, req.status, req.reviewNotes)
                    call.respond(HttpStatusCode.OK, JsonData.ok("reviewed"))
                }

                delete("/{id}") {
                    val videoId = call.pathParameters["id"]?.toLongOrNull()
                        ?: return@delete call.respond(
                            HttpStatusCode.BadRequest,
                            JsonData.error(400, "invalid video id"),
                        )

                    adminService.adminDeleteVideo(videoId)
                    call.respond(HttpStatusCode.OK, JsonData.ok("deleted"))
                }
            }

            // ── Admin Users ───────────────────────────────
            route("/admin-users") {
                get {
                    val page = call.queryParameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.queryParameters["page_size"]?.toIntOrNull() ?: 20
                    val username = call.queryParameters["username"]
                    val role = call.queryParameters["role"]?.toShortOrNull()
                    val status = call.queryParameters["status"]?.toShortOrNull()

                    val (items, total) = adminService.listAdmins(page, pageSize, username, role, status)
                    call.respond(
                        HttpStatusCode.OK,
                        JsonData.ok(Paginated(items.map { it.toDto() }, total, page, pageSize)),
                    )
                }

                post {
                    val principal = call.adminPrincipal()
                    val req = call.receive<CreateAdminRequest>()
                    val id = adminService.createAdmin(
                        currentRole = principal.role,
                        username = req.username,
                        password = req.password,
                        role = req.role,
                        fullName = req.fullName,
                    )
                    call.respond(HttpStatusCode.Created, JsonData.ok(mapOf("id" to id)))
                }

                get("/{id}") {
                    val adminId = call.pathParameters["id"]?.toLongOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            JsonData.error(400, "invalid admin id"),
                        )

                    val admin = adminService.getAdmin(adminId)
                    call.respond(HttpStatusCode.OK, JsonData.ok(admin.toDto()))
                }

                put("/{id}") {
                    val principal = call.adminPrincipal()
                    val targetId = call.pathParameters["id"]?.toLongOrNull()
                        ?: return@put call.respond(
                            HttpStatusCode.BadRequest,
                            JsonData.error(400, "invalid admin id"),
                        )

                    val req = call.receive<UpdateAdminRequest>()
                    adminService.updateAdmin(
                        currentRole = principal.role,
                        targetId = targetId,
                        role = req.role,
                        fullName = req.fullName,
                        phone = req.phone,
                        status = req.status,
                    )
                    call.respond(HttpStatusCode.OK, JsonData.ok("updated"))
                }

                delete("/{id}") {
                    val principal = call.adminPrincipal()
                    val targetId = call.pathParameters["id"]?.toLongOrNull()
                        ?: return@delete call.respond(
                            HttpStatusCode.BadRequest,
                            JsonData.error(400, "invalid admin id"),
                        )

                    adminService.deleteAdmin(principal.role, principal.adminId, targetId)
                    call.respond(HttpStatusCode.OK, JsonData.ok("deleted"))
                }

                put("/{id}/password") {
                    val principal = call.adminPrincipal()
                    val targetId = call.pathParameters["id"]?.toLongOrNull()
                        ?: return@put call.respond(
                            HttpStatusCode.BadRequest,
                            JsonData.error(400, "invalid admin id"),
                        )

                    val req = call.receive<AdminChangePasswordRequest>()
                    adminService.changeAdminPassword(
                        currentAdminId = principal.adminId,
                        currentRole = principal.role,
                        targetId = targetId,
                        newPassword = req.newPassword,
                    )
                    call.respond(HttpStatusCode.OK, JsonData.ok("password changed"))
                }
            }

            // ── Customers ─────────────────────────────────
            route("/customers") {
                get {
                    val page = call.queryParameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.queryParameters["page_size"]?.toIntOrNull() ?: 20
                    val name = call.queryParameters["name"]
                    val isVip = call.queryParameters["is_vip"]?.toBooleanStrictOrNull()

                    val (items, total) = adminService.listCustomers(page, pageSize, name, isVip)
                    call.respond(
                        HttpStatusCode.OK,
                        JsonData.ok(Paginated(items.map { it.toCustomerListDto() }, total, page, pageSize)),
                    )
                }

                put("/{id}/password") {
                    val customerId = call.pathParameters["id"]?.toLongOrNull()
                        ?: return@put call.respond(
                            HttpStatusCode.BadRequest,
                            JsonData.error(400, "invalid customer id"),
                        )

                    val req = call.receive<ResetCustomerPasswordRequest>()
                    adminService.resetCustomerPassword(customerId, req.newPassword)
                    call.respond(HttpStatusCode.OK, JsonData.ok("password reset"))
                }
            }
        }
    }
}

// ── Extension mappers ───────────────────────────────────────

private fun TokenResponse.toAdminDto(): AdminTokenResponseDto = AdminTokenResponseDto(
    accessToken = accessToken,
    refreshToken = refreshToken,
)

private fun AdminUserRow.toDto(): AdminUserDto = AdminUserDto(
    id = id,
    username = username,
    role = role,
    fullName = fullName,
    phone = phone,
    avatarUrl = avatarUrl,
    status = status,
    lastLoginAt = lastLoginAt?.toString(),
    createdAt = createdAt.toString(),
)

private fun AdminVideoListRow.toDto(): AdminVideoListItemDto = AdminVideoListItemDto(
    id = id,
    customerId = customerId,
    title = title,
    fileName = fileName,
    fileUrl = fileUrl,
    thumbnailUrl = thumbnailUrl,
    mimeType = mimeType,
    status = status,
    reviewNotes = reviewNotes,
    reviewedBy = reviewedBy,
    totalClicks = totalClicks,
    validClicks = validClicks,
    likeCount = likeCount,
    createdAt = createdAt.toString(),
    authorName = authorName,
    authorAvatar = authorAvatar,
    authorIsVip = authorIsVip,
)

private fun AdminVideoRow.toDto(): AdminVideoDetailDto = AdminVideoDetailDto(
    id = id,
    customerId = customerId,
    title = title,
    description = description,
    fileName = fileName,
    fileSize = fileSize,
    fileUrl = fileUrl,
    thumbnailUrl = thumbnailUrl,
    previewGifUrl = previewGifUrl,
    mimeType = mimeType,
    duration = duration,
    width = width,
    height = height,
    status = status,
    reviewNotes = reviewNotes,
    reviewedBy = reviewedBy,
    reviewedAt = reviewedAt?.toString(),
    totalClicks = totalClicks,
    validClicks = validClicks,
    likeCount = likeCount,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    authorName = authorName,
    authorAvatar = authorAvatar,
    authorIsVip = authorIsVip,
)

private fun CustomerRow.toCustomerListDto(): CustomerListDto = CustomerListDto(
    id = id,
    username = username,
    name = name,
    phone = phone,
    email = email,
    avatarUrl = avatarUrl,
    isVip = isVip,
    diamondBalance = diamondBalance,
    createdAt = createdAt.toString(),
)
