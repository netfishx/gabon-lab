package lab.gabon.route

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import lab.gabon.model.JsonData
import lab.gabon.repository.RevenueReportRow
import lab.gabon.repository.VideoDailyReportRow
import lab.gabon.repository.VideoSummaryReportRow
import lab.gabon.service.ReportService

// ── Response DTOs ───────────────────────────────────────────

@Serializable
data class RevenueReportDto(
    val date: String,
    val claimCount: Long,
    val totalDiamonds: Long,
)

@Serializable
data class VideoDailyReportDto(
    val date: String,
    val uploadCount: Long,
    val totalClicks: Long,
    val totalValidClicks: Long,
    val totalLikes: Long,
)

@Serializable
data class VideoSummaryReportDto(
    val totalVideos: Long,
    val approvedCount: Long,
    val pendingCount: Long,
    val rejectedCount: Long,
    val totalClicks: Long,
    val totalValidClicks: Long,
    val totalLikes: Long,
)

// ── Route Registration ──────────────────────────────────────

fun Route.reportRoutes(reportService: ReportService) {
    route("/reports") {
        get("/revenue") {
            val startDate = call.queryParameters["start_date"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    JsonData.error(400, "start_date is required"),
                )
            val endDate = call.queryParameters["end_date"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    JsonData.error(400, "end_date is required"),
                )

            val rows = reportService.revenueReport(startDate, endDate)
            call.respond(HttpStatusCode.OK, JsonData.ok(rows.map { it.toDto() }))
        }

        route("/video") {
            get("/daily") {
                val startDate = call.queryParameters["start_date"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        JsonData.error(400, "start_date is required"),
                    )
                val endDate = call.queryParameters["end_date"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        JsonData.error(400, "end_date is required"),
                    )

                val rows = reportService.videoDailyReport(startDate, endDate)
                call.respond(HttpStatusCode.OK, JsonData.ok(rows.map { it.toDto() }))
            }

            get("/summary") {
                val startDate = call.queryParameters["start_date"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        JsonData.error(400, "start_date is required"),
                    )
                val endDate = call.queryParameters["end_date"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        JsonData.error(400, "end_date is required"),
                    )

                val summary = reportService.videoSummaryReport(startDate, endDate)
                call.respond(HttpStatusCode.OK, JsonData.ok(summary.toDto()))
            }
        }
    }
}

// ── Extension mappers ───────────────────────────────────────

private fun RevenueReportRow.toDto(): RevenueReportDto = RevenueReportDto(
    date = date,
    claimCount = claimCount,
    totalDiamonds = totalDiamonds,
)

private fun VideoDailyReportRow.toDto(): VideoDailyReportDto = VideoDailyReportDto(
    date = date,
    uploadCount = uploadCount,
    totalClicks = totalClicks,
    totalValidClicks = totalValidClicks,
    totalLikes = totalLikes,
)

private fun VideoSummaryReportRow.toDto(): VideoSummaryReportDto = VideoSummaryReportDto(
    totalVideos = totalVideos,
    approvedCount = approvedCount,
    pendingCount = pendingCount,
    rejectedCount = rejectedCount,
    totalClicks = totalClicks,
    totalValidClicks = totalValidClicks,
    totalLikes = totalLikes,
)
