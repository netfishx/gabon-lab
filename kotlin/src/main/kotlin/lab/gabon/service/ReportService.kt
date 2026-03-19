package lab.gabon.service

import lab.gabon.model.AppError
import lab.gabon.model.AppException
import lab.gabon.repository.ReportRepo
import lab.gabon.repository.RevenueReportRow
import lab.gabon.repository.VideoDailyReportRow
import lab.gabon.repository.VideoSummaryReportRow
import java.time.LocalDate
import java.time.format.DateTimeParseException

class ReportService(
    private val reportRepo: ReportRepo,
) {
    suspend fun revenueReport(
        startDate: String,
        endDate: String,
    ): List<RevenueReportRow> {
        validateDateRange(startDate, endDate)
        return reportRepo.revenueReport(startDate, endDate)
    }

    suspend fun videoDailyReport(
        startDate: String,
        endDate: String,
    ): List<VideoDailyReportRow> {
        validateDateRange(startDate, endDate)
        return reportRepo.videoDailyReport(startDate, endDate)
    }

    suspend fun videoSummaryReport(
        startDate: String,
        endDate: String,
    ): VideoSummaryReportRow {
        validateDateRange(startDate, endDate)
        return reportRepo.videoSummaryReport(startDate, endDate)
    }

    private fun validateDateRange(
        startDate: String,
        endDate: String,
    ) {
        val start = parseDate(startDate, "start_date")
        val end = parseDate(endDate, "end_date")
        if (start.isAfter(end)) {
            throw AppException(AppError.BadRequest("start_date must not be after end_date"))
        }
    }

    private fun parseDate(
        value: String,
        paramName: String,
    ): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            throw AppException(AppError.BadRequest("invalid $paramName format, expected yyyy-MM-dd"))
        }
}
