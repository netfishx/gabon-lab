package lab.gabon.route

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import lab.gabon.model.JsonData
import lab.gabon.model.Paginated
import lab.gabon.plugin.CustomerPrincipal
import lab.gabon.plugin.customerPrincipal
import lab.gabon.repository.VideoListRow
import lab.gabon.service.PresignResult
import lab.gabon.service.VideoDetail
import lab.gabon.service.VideoService

// ── Request DTOs ────────────────────────────────────────────

@Serializable
data class PresignUploadRequest(
    val fileName: String,
    val contentType: String,
)

@Serializable
data class ConfirmUploadRequest(
    val s3Key: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val title: String? = null,
    val description: String? = null,
)

// ── Response DTOs ───────────────────────────────────────────

@Serializable
data class PresignResultDto(
    val uploadUrl: String,
    val fileUrl: String,
    val s3Key: String,
)

@Serializable
data class ConfirmUploadDto(
    val videoId: Long,
    val status: Short,
)

@Serializable
data class VideoListItemDto(
    val id: Long,
    val customerId: Long,
    val title: String?,
    val fileName: String,
    val fileUrl: String,
    val thumbnailUrl: String?,
    val mimeType: String,
    val status: Short,
    val totalClicks: Long,
    val validClicks: Long,
    val likeCount: Long,
    val createdAt: String,
    val uploaderName: String?,
    val uploaderAvatar: String?,
)

@Serializable
data class VideoDetailDto(
    val id: Long,
    val customerId: Long,
    val title: String?,
    val description: String?,
    val fileName: String,
    val fileSize: Long,
    val fileUrl: String,
    val thumbnailUrl: String?,
    val mimeType: String,
    val duration: Int?,
    val status: Short,
    val totalClicks: Long,
    val validClicks: Long,
    val likeCount: Long,
    val createdAt: String,
    val uploaderName: String?,
    val uploaderAvatar: String?,
    val isLiked: Boolean,
)

// ── Route Registration ──────────────────────────────────────

fun Route.videoRoutes(videoService: VideoService) {
    route("/videos") {
        // Public routes (no auth needed)
        get {
            val page = call.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.queryParameters["page_size"]?.toIntOrNull() ?: 10
            val keyword = call.queryParameters["keyword"]

            val (items, total) = videoService.listVideos(page, pageSize, keyword)
            call.respond(
                HttpStatusCode.OK,
                JsonData.ok(Paginated(items.map { it.toDto() }, total, page, pageSize)),
            )
        }

        get("/featured") {
            val page = call.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.queryParameters["page_size"]?.toIntOrNull() ?: 10

            val (items, total) = videoService.listFeatured(page, pageSize)
            call.respond(
                HttpStatusCode.OK,
                JsonData.ok(Paginated(items.map { it.toDto() }, total, page, pageSize)),
            )
        }

        // Optional auth routes (public, but extract principal if present)
        authenticate("customer", optional = true) {
            get("/{id}") {
                val videoId =
                    call.pathParameters["id"]?.toLongOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, JsonData.error(400, "invalid video id"))

                val customerId = call.principal<CustomerPrincipal>()?.customerId

                val detail = videoService.getDetail(videoId, customerId)
                call.respond(HttpStatusCode.OK, JsonData.ok(detail.toDto()))
            }

            post("/{videoId}/play-click") {
                val videoId =
                    call.pathParameters["videoId"]?.toLongOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, JsonData.error(400, "invalid video id"))

                val customerId = call.principal<CustomerPrincipal>()?.customerId
                val ipAddress = call.request.local.remoteAddress

                videoService.recordPlayClick(videoId, customerId, ipAddress)
                call.respond(HttpStatusCode.OK, JsonData.ok("ok"))
            }

            post("/{videoId}/play-valid") {
                val videoId =
                    call.pathParameters["videoId"]?.toLongOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, JsonData.error(400, "invalid video id"))

                val customerId = call.principal<CustomerPrincipal>()?.customerId
                val ipAddress = call.request.local.remoteAddress

                videoService.recordValidPlay(videoId, customerId, ipAddress)
                call.respond(HttpStatusCode.OK, JsonData.ok("ok"))
            }
        }

        // Authenticated routes (require customer auth)
        authenticate("customer") {
            post("/{id}/like") {
                val principal = call.customerPrincipal()
                val videoId =
                    call.pathParameters["id"]?.toLongOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, JsonData.error(400, "invalid video id"))

                videoService.likeVideo(videoId, principal.customerId)
                call.respond(HttpStatusCode.OK, JsonData.ok("liked"))
            }

            delete("/{id}/like") {
                val principal = call.customerPrincipal()
                val videoId =
                    call.pathParameters["id"]?.toLongOrNull()
                        ?: return@delete call.respond(
                            HttpStatusCode.BadRequest,
                            JsonData.error(400, "invalid video id"),
                        )

                videoService.unlikeVideo(videoId, principal.customerId)
                call.respond(HttpStatusCode.OK, JsonData.ok("unliked"))
            }

            post("/upload-url") {
                val principal = call.customerPrincipal()
                val req = call.receive<PresignUploadRequest>()

                val result = videoService.presignUpload(principal.customerId, req.fileName, req.contentType)
                call.respond(HttpStatusCode.OK, JsonData.ok(result.toDto()))
            }

            post("/confirm-upload") {
                val principal = call.customerPrincipal()
                val req = call.receive<ConfirmUploadRequest>()

                val videoId =
                    videoService.confirmUpload(
                        customerId = principal.customerId,
                        s3Key = req.s3Key,
                        fileName = req.fileName,
                        fileSize = req.fileSize,
                        mimeType = req.mimeType,
                        title = req.title,
                        description = req.description,
                    )
                call.respond(
                    HttpStatusCode.Created,
                    JsonData.ok(ConfirmUploadDto(videoId = videoId, status = 3)),
                )
            }

            get("/me") {
                val principal = call.customerPrincipal()
                val page = call.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.queryParameters["page_size"]?.toIntOrNull() ?: 10
                val status = call.queryParameters["status"]?.toShortOrNull()

                val (items, total) = videoService.listMyVideos(principal.customerId, page, pageSize, status)
                call.respond(
                    HttpStatusCode.OK,
                    JsonData.ok(Paginated(items.map { it.toDto() }, total, page, pageSize)),
                )
            }

            delete("/{id}") {
                val principal = call.customerPrincipal()
                val videoId =
                    call.pathParameters["id"]?.toLongOrNull()
                        ?: return@delete call.respond(
                            HttpStatusCode.BadRequest,
                            JsonData.error(400, "invalid video id"),
                        )

                videoService.deleteVideo(videoId, principal.customerId)
                call.respond(HttpStatusCode.OK, JsonData.ok("deleted"))
            }
        }
    }
}

fun Route.userVideoRoutes(videoService: VideoService) {
    route("/users/{userId}/videos") {
        get {
            val userId =
                call.pathParameters["userId"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, JsonData.error(400, "invalid user id"))

            val page = call.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.queryParameters["page_size"]?.toIntOrNull() ?: 10

            val (items, total) = videoService.listUserVideos(userId, page, pageSize)
            call.respond(
                HttpStatusCode.OK,
                JsonData.ok(Paginated(items.map { it.toDto() }, total, page, pageSize)),
            )
        }
    }
}

// ── Extension mappers ───────────────────────────────────────

private fun PresignResult.toDto(): PresignResultDto =
    PresignResultDto(
        uploadUrl = uploadUrl,
        fileUrl = fileUrl,
        s3Key = s3Key,
    )

private fun VideoListRow.toDto(): VideoListItemDto =
    VideoListItemDto(
        id = id,
        customerId = customerId,
        title = title,
        fileName = fileName,
        fileUrl = fileUrl,
        thumbnailUrl = thumbnailUrl,
        mimeType = mimeType,
        status = status,
        totalClicks = totalClicks,
        validClicks = validClicks,
        likeCount = likeCount,
        createdAt = createdAt.toString(),
        uploaderName = uploaderName,
        uploaderAvatar = uploaderAvatar,
    )

private fun VideoDetail.toDto(): VideoDetailDto =
    VideoDetailDto(
        id = id,
        customerId = customerId,
        title = title,
        description = description,
        fileName = fileName,
        fileSize = fileSize,
        fileUrl = fileUrl,
        thumbnailUrl = thumbnailUrl,
        mimeType = mimeType,
        duration = duration,
        status = status,
        totalClicks = totalClicks,
        validClicks = validClicks,
        likeCount = likeCount,
        createdAt = createdAt.toString(),
        uploaderName = uploaderName,
        uploaderAvatar = uploaderAvatar,
        isLiked = isLiked,
    )
