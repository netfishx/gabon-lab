package lab.gabon.repository

import kotlinx.datetime.Instant
import lab.gabon.config.dbQuery
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class AdminVideoRow(
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
    val reviewedAt: Instant?,
    val totalClicks: Long,
    val validClicks: Long,
    val likeCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    // joined
    val authorName: String?,
    val authorAvatar: String?,
    val authorIsVip: Boolean,
)

data class AdminVideoListRow(
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
    val createdAt: Instant,
    val authorName: String?,
    val authorAvatar: String?,
    val authorIsVip: Boolean,
)

class AdminVideoRepo {
    suspend fun listVideosAdmin(
        page: Int,
        pageSize: Int,
        status: Short? = null,
        authorName: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        isVip: Boolean? = null,
    ): Pair<List<AdminVideoListRow>, Long> =
        dbQuery {
            var condition: Op<Boolean> = Videos.deletedAt.isNull()

            if (status != null) {
                condition = condition and (Videos.status eq status)
            }
            if (!authorName.isNullOrBlank()) {
                condition = condition and
                    (Customers.name.lowerCase() like LikePattern("%${authorName.lowercase()}%"))
            }
            if (startDate != null) {
                val start = LocalDate.parse(startDate).atStartOfDay().atOffset(ZoneOffset.UTC)
                condition = condition and (Videos.createdAt greaterEq start)
            }
            if (endDate != null) {
                // end_date is exclusive: internally add 1 day
                val end =
                    LocalDate
                        .parse(endDate)
                        .plusDays(1)
                        .atStartOfDay()
                        .atOffset(ZoneOffset.UTC)
                condition = condition and (Videos.createdAt less end)
            }
            if (isVip != null) {
                condition = condition and (Customers.isVip eq isVip)
            }

            val total =
                (Videos innerJoin Customers)
                    .selectAll()
                    .where { condition }
                    .count()

            val items =
                (Videos innerJoin Customers)
                    .selectAll()
                    .where { condition }
                    .orderBy(Videos.createdAt to SortOrder.DESC)
                    .limit(pageSize)
                    .offset(((page - 1) * pageSize).toLong())
                    .map { it.toAdminVideoListRow() }

            items to total
        }

    suspend fun getVideoDetailAdmin(videoId: Long): AdminVideoRow? =
        dbQuery {
            (Videos innerJoin Customers)
                .selectAll()
                .where {
                    (Videos.id eq videoId) and Videos.deletedAt.isNull()
                }.singleOrNull()
                ?.toAdminVideoRow()
        }

    suspend fun reviewVideo(
        videoId: Long,
        adminId: Long,
        status: Short,
        reviewNotes: String?,
    ): Boolean =
        dbQuery {
            Videos.update({
                (Videos.id eq videoId) and Videos.deletedAt.isNull()
            }) {
                it[Videos.status] = status
                it[Videos.reviewNotes] = reviewNotes
                it[Videos.reviewedBy] = adminId
                it[Videos.reviewedAt] = CurrentTimestampWithTimeZone
                it[updatedAt] = CurrentTimestampWithTimeZone
            } > 0
        }

    suspend fun adminDeleteVideo(videoId: Long): Boolean =
        dbQuery {
            Videos.update({
                (Videos.id eq videoId) and Videos.deletedAt.isNull()
            }) {
                it[deletedAt] = CurrentTimestampWithTimeZone
                it[updatedAt] = CurrentTimestampWithTimeZone
            } > 0
        }

    suspend fun listCustomers(
        page: Int,
        pageSize: Int,
        name: String? = null,
        isVip: Boolean? = null,
    ): Pair<List<CustomerRow>, Long> =
        dbQuery {
            var condition: Op<Boolean> = Customers.deletedAt.isNull()

            if (!name.isNullOrBlank()) {
                condition = condition and
                    (Customers.name.lowerCase() like LikePattern("%${name.lowercase()}%"))
            }
            if (isVip != null) {
                condition = condition and (Customers.isVip eq isVip)
            }

            val total =
                Customers
                    .selectAll()
                    .where { condition }
                    .count()

            val items =
                Customers
                    .selectAll()
                    .where { condition }
                    .orderBy(Customers.id to SortOrder.ASC)
                    .limit(pageSize)
                    .offset(((page - 1) * pageSize).toLong())
                    .map { it.toCustomerRow() }

            items to total
        }

    private fun ResultRow.toAdminVideoRow(): AdminVideoRow =
        AdminVideoRow(
            id = this[Videos.id].value,
            customerId = this[Videos.customerId].value,
            title = this[Videos.title],
            description = this[Videos.description],
            fileName = this[Videos.fileName],
            fileSize = this[Videos.fileSize],
            fileUrl = this[Videos.fileUrl],
            thumbnailUrl = this[Videos.thumbnailUrl],
            previewGifUrl = this[Videos.previewGifUrl],
            mimeType = this[Videos.mimeType],
            duration = this[Videos.duration],
            width = this[Videos.width],
            height = this[Videos.height],
            status = this[Videos.status],
            reviewNotes = this[Videos.reviewNotes],
            reviewedBy = this[Videos.reviewedBy]?.value,
            reviewedAt = this[Videos.reviewedAt]?.toKotlinInstant(),
            totalClicks = this[Videos.totalClicks],
            validClicks = this[Videos.validClicks],
            likeCount = this[Videos.likeCount],
            createdAt = this[Videos.createdAt].toKotlinInstant(),
            updatedAt = this[Videos.updatedAt].toKotlinInstant(),
            authorName = this[Customers.name],
            authorAvatar = this[Customers.avatarUrl],
            authorIsVip = this[Customers.isVip],
        )

    private fun ResultRow.toAdminVideoListRow(): AdminVideoListRow =
        AdminVideoListRow(
            id = this[Videos.id].value,
            customerId = this[Videos.customerId].value,
            title = this[Videos.title],
            fileName = this[Videos.fileName],
            fileUrl = this[Videos.fileUrl],
            thumbnailUrl = this[Videos.thumbnailUrl],
            mimeType = this[Videos.mimeType],
            status = this[Videos.status],
            reviewNotes = this[Videos.reviewNotes],
            reviewedBy = this[Videos.reviewedBy]?.value,
            totalClicks = this[Videos.totalClicks],
            validClicks = this[Videos.validClicks],
            likeCount = this[Videos.likeCount],
            createdAt = this[Videos.createdAt].toKotlinInstant(),
            authorName = this[Customers.name],
            authorAvatar = this[Customers.avatarUrl],
            authorIsVip = this[Customers.isVip],
        )

    private fun ResultRow.toCustomerRow(): CustomerRow =
        CustomerRow(
            id = this[Customers.id].value,
            username = this[Customers.username],
            passwordHash = this[Customers.passwordHash],
            name = this[Customers.name],
            phone = this[Customers.phone],
            email = this[Customers.email],
            avatarUrl = this[Customers.avatarUrl],
            signature = this[Customers.signature],
            isVip = this[Customers.isVip],
            diamondBalance = this[Customers.diamondBalance],
            lastLoginAt = this[Customers.lastLoginAt]?.toKotlinInstant(),
            createdAt = this[Customers.createdAt].toKotlinInstant(),
            updatedAt = this[Customers.updatedAt].toKotlinInstant(),
        )

    private fun OffsetDateTime.toKotlinInstant(): Instant = Instant.fromEpochSeconds(toEpochSecond(), nano)
}
