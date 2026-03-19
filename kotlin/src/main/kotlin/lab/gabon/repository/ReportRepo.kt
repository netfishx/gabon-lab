package lab.gabon.repository

import lab.gabon.config.dbQuery
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

data class RevenueReportRow(
    val date: String,
    val claimCount: Long,
    val totalDiamonds: Long,
)

data class VideoDailyReportRow(
    val date: String,
    val uploadCount: Long,
    val totalClicks: Long,
    val totalValidClicks: Long,
    val totalLikes: Long,
)

data class VideoSummaryReportRow(
    val totalVideos: Long,
    val approvedCount: Long,
    val pendingCount: Long,
    val rejectedCount: Long,
    val totalClicks: Long,
    val totalValidClicks: Long,
    val totalLikes: Long,
)

class ReportRepo {

    suspend fun revenueReport(startDate: String, endDate: String): List<RevenueReportRow> = dbQuery {
        val sql = """
            SELECT DATE(claimed_at) AS date,
                   COUNT(*)          AS claim_count,
                   SUM(reward_diamonds) AS total_diamonds
            FROM task_progress
            WHERE task_status = 3
              AND DATE(claimed_at) >= ?::date
              AND DATE(claimed_at) <= ?::date
            GROUP BY DATE(claimed_at)
            ORDER BY date
        """.trimIndent()

        val jdbcConn = TransactionManager.current().connection.connection as java.sql.Connection
        val rows = mutableListOf<RevenueReportRow>()
        jdbcConn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, startDate)
            stmt.setString(2, endDate)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                rows += RevenueReportRow(
                    date = rs.getString("date"),
                    claimCount = rs.getLong("claim_count"),
                    totalDiamonds = rs.getLong("total_diamonds"),
                )
            }
        }
        rows
    }

    suspend fun videoDailyReport(startDate: String, endDate: String): List<VideoDailyReportRow> = dbQuery {
        val sql = """
            SELECT DATE(created_at)      AS date,
                   COUNT(*)              AS upload_count,
                   SUM(total_clicks)     AS total_clicks,
                   SUM(valid_clicks)     AS total_valid_clicks,
                   SUM(like_count)       AS total_likes
            FROM videos
            WHERE DATE(created_at) >= ?::date
              AND DATE(created_at) <= ?::date
            GROUP BY DATE(created_at)
            ORDER BY date
        """.trimIndent()

        val jdbcConn = TransactionManager.current().connection.connection as java.sql.Connection
        val rows = mutableListOf<VideoDailyReportRow>()
        jdbcConn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, startDate)
            stmt.setString(2, endDate)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                rows += VideoDailyReportRow(
                    date = rs.getString("date"),
                    uploadCount = rs.getLong("upload_count"),
                    totalClicks = rs.getLong("total_clicks"),
                    totalValidClicks = rs.getLong("total_valid_clicks"),
                    totalLikes = rs.getLong("total_likes"),
                )
            }
        }
        rows
    }

    suspend fun videoSummaryReport(startDate: String, endDate: String): VideoSummaryReportRow = dbQuery {
        val sql = """
            SELECT COUNT(*)                                          AS total_videos,
                   COUNT(*) FILTER (WHERE status = 4)                AS approved_count,
                   COUNT(*) FILTER (WHERE status = 3)                AS pending_count,
                   COUNT(*) FILTER (WHERE status = 5)                AS rejected_count,
                   COALESCE(SUM(total_clicks), 0)                    AS total_clicks,
                   COALESCE(SUM(valid_clicks), 0)                    AS total_valid_clicks,
                   COALESCE(SUM(like_count), 0)                      AS total_likes
            FROM videos
            WHERE DATE(created_at) >= ?::date
              AND DATE(created_at) <= ?::date
        """.trimIndent()

        val jdbcConn = TransactionManager.current().connection.connection as java.sql.Connection
        jdbcConn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, startDate)
            stmt.setString(2, endDate)
            val rs = stmt.executeQuery()
            rs.next()
            VideoSummaryReportRow(
                totalVideos = rs.getLong("total_videos"),
                approvedCount = rs.getLong("approved_count"),
                pendingCount = rs.getLong("pending_count"),
                rejectedCount = rs.getLong("rejected_count"),
                totalClicks = rs.getLong("total_clicks"),
                totalValidClicks = rs.getLong("total_valid_clicks"),
                totalLikes = rs.getLong("total_likes"),
            )
        }
    }
}
