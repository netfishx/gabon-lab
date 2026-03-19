package lab.gabon.repository

import kotlinx.datetime.Instant
import lab.gabon.config.dbQuery
import lab.gabon.model.VideoStatus
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.longLiteral
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

data class VideoRow(
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
    val createdAt: Instant,
    val updatedAt: Instant,
    // joined uploader info
    val uploaderName: String?,
    val uploaderAvatar: String?,
)

data class VideoListRow(
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
    val createdAt: Instant,
    val uploaderName: String?,
    val uploaderAvatar: String?,
)

class VideoRepo {
    suspend fun create(
        customerId: Long,
        title: String?,
        description: String?,
        fileName: String,
        fileSize: Long,
        fileUrl: String,
        mimeType: String,
        status: Short = VideoStatus.PENDING_REVIEW.value,
    ): Long =
        dbQuery {
            Videos
                .insertAndGetId {
                    it[Videos.customerId] = customerId
                    it[Videos.title] = title
                    it[Videos.description] = description
                    it[Videos.fileName] = fileName
                    it[Videos.fileSize] = fileSize
                    it[Videos.fileUrl] = fileUrl
                    it[Videos.mimeType] = mimeType
                    it[Videos.status] = status
                }.value
        }

    suspend fun findById(id: Long): VideoRow? =
        dbQuery {
            (Videos innerJoin Customers)
                .selectAll()
                .where {
                    (Videos.id eq id) and Videos.deletedAt.isNull()
                }.singleOrNull()
                ?.toVideoRow()
        }

    suspend fun isLikedBy(
        videoId: Long,
        customerId: Long,
    ): Boolean =
        dbQuery {
            VideoLikes
                .selectAll()
                .where {
                    (VideoLikes.videoId eq videoId) and (VideoLikes.customerId eq customerId)
                }.count() > 0
        }

    suspend fun listApproved(
        page: Int,
        pageSize: Int,
        keyword: String? = null,
    ): Pair<List<VideoListRow>, Long> =
        dbQuery {
            val baseCondition = (Videos.status eq VideoStatus.APPROVED.value) and Videos.deletedAt.isNull()
            val condition =
                if (!keyword.isNullOrBlank()) {
                    baseCondition and (Videos.title.lowerCase() like LikePattern("%${keyword.lowercase()}%"))
                } else {
                    baseCondition
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
                    .map { it.toVideoListRow() }

            items to total
        }

    suspend fun listFeatured(
        page: Int,
        pageSize: Int,
    ): Pair<List<VideoListRow>, Long> =
        dbQuery {
            val condition = (Videos.status eq VideoStatus.APPROVED.value) and Videos.deletedAt.isNull()

            val total =
                (Videos innerJoin Customers)
                    .selectAll()
                    .where { condition }
                    .count()

            val items =
                (Videos innerJoin Customers)
                    .selectAll()
                    .where { condition }
                    .orderBy(Videos.likeCount to SortOrder.DESC)
                    .limit(pageSize)
                    .offset(((page - 1) * pageSize).toLong())
                    .map { it.toVideoListRow() }

            items to total
        }

    suspend fun listByCustomer(
        customerId: Long,
        page: Int,
        pageSize: Int,
        status: Short? = null,
    ): Pair<List<VideoListRow>, Long> =
        dbQuery {
            val baseCondition = (Videos.customerId eq customerId) and Videos.deletedAt.isNull()
            val condition =
                if (status != null) {
                    baseCondition and (Videos.status eq status)
                } else {
                    baseCondition
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
                    .map { it.toVideoListRow() }

            items to total
        }

    suspend fun listApprovedByUser(
        userId: Long,
        page: Int,
        pageSize: Int,
    ): Pair<List<VideoListRow>, Long> =
        dbQuery {
            val condition =
                (Videos.customerId eq userId) and
                    (Videos.status eq VideoStatus.APPROVED.value) and
                    Videos.deletedAt.isNull()

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
                    .map { it.toVideoListRow() }

            items to total
        }

    suspend fun softDelete(
        id: Long,
        customerId: Long,
    ): Boolean =
        dbQuery {
            Videos.update({
                (Videos.id eq id) and (Videos.customerId eq customerId) and Videos.deletedAt.isNull()
            }) {
                it[deletedAt] = CurrentTimestampWithTimeZone
                it[updatedAt] = CurrentTimestampWithTimeZone
            } > 0
        }

    suspend fun incrementTotalClicks(id: Long): Unit =
        dbQuery {
            Videos.update({ Videos.id eq id }) {
                it[totalClicks] = totalClicks plus longLiteral(1)
            }
        }

    suspend fun incrementValidClicks(id: Long): Unit =
        dbQuery {
            Videos.update({ Videos.id eq id }) {
                it[validClicks] = validClicks plus longLiteral(1)
            }
        }

    /**
     * Atomically insert a like and increment like_count using a CTE.
     * ON CONFLICT DO NOTHING makes this idempotent — duplicate likes won't increment.
     * Returns true if the like was actually inserted (not a duplicate).
     */
    @Suppress("MagicNumber")
    suspend fun likeVideo(
        videoId: Long,
        customerId: Long,
    ): Boolean =
        dbQuery {
            val sql =
                """
                WITH inserted AS (
                    INSERT INTO video_likes (video_id, customer_id)
                    VALUES (?, ?)
                    ON CONFLICT (video_id, customer_id) DO NOTHING
                    RETURNING id
                )
                UPDATE videos SET like_count = like_count + 1, updated_at = NOW()
                WHERE id = ? AND EXISTS (SELECT 1 FROM inserted)
                """.trimIndent()

            val jdbcConn = TransactionManager.current().connection.connection as java.sql.Connection
            jdbcConn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, videoId)
                stmt.setLong(2, customerId)
                stmt.setLong(3, videoId)
                stmt.executeUpdate() > 0
            }
        }

    /**
     * Atomically delete a like and decrement like_count using a CTE.
     * If the user hasn't liked the video, DELETE returns no rows so UPDATE doesn't fire.
     * GREATEST prevents negative counts.
     * Returns true if the like was actually removed.
     */
    @Suppress("MagicNumber")
    suspend fun unlikeVideo(
        videoId: Long,
        customerId: Long,
    ): Boolean =
        dbQuery {
            val sql =
                """
                WITH deleted AS (
                    DELETE FROM video_likes
                    WHERE video_id = ? AND customer_id = ?
                    RETURNING id
                )
                UPDATE videos SET like_count = GREATEST(like_count - 1, 0), updated_at = NOW()
                WHERE id = ? AND EXISTS (SELECT 1 FROM deleted)
                """.trimIndent()

            val jdbcConn = TransactionManager.current().connection.connection as java.sql.Connection
            jdbcConn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, videoId)
                stmt.setLong(2, customerId)
                stmt.setLong(3, videoId)
                stmt.executeUpdate() > 0
            }
        }

    private fun ResultRow.toVideoRow(): VideoRow =
        VideoRow(
            id = this[Videos.id].value,
            customerId = this[Videos.customerId].value,
            title = this[Videos.title],
            description = this[Videos.description],
            fileName = this[Videos.fileName],
            fileSize = this[Videos.fileSize],
            fileUrl = this[Videos.fileUrl],
            thumbnailUrl = this[Videos.thumbnailUrl],
            mimeType = this[Videos.mimeType],
            duration = this[Videos.duration],
            status = this[Videos.status],
            totalClicks = this[Videos.totalClicks],
            validClicks = this[Videos.validClicks],
            likeCount = this[Videos.likeCount],
            createdAt = this[Videos.createdAt].toKotlinInstant(),
            updatedAt = this[Videos.updatedAt].toKotlinInstant(),
            uploaderName = this[Customers.name],
            uploaderAvatar = this[Customers.avatarUrl],
        )

    private fun ResultRow.toVideoListRow(): VideoListRow =
        VideoListRow(
            id = this[Videos.id].value,
            customerId = this[Videos.customerId].value,
            title = this[Videos.title],
            fileName = this[Videos.fileName],
            fileUrl = this[Videos.fileUrl],
            thumbnailUrl = this[Videos.thumbnailUrl],
            mimeType = this[Videos.mimeType],
            status = this[Videos.status],
            totalClicks = this[Videos.totalClicks],
            validClicks = this[Videos.validClicks],
            likeCount = this[Videos.likeCount],
            createdAt = this[Videos.createdAt].toKotlinInstant(),
            uploaderName = this[Customers.name],
            uploaderAvatar = this[Customers.avatarUrl],
        )

    private fun OffsetDateTime.toKotlinInstant(): Instant = Instant.fromEpochSeconds(toEpochSecond(), nano)
}
