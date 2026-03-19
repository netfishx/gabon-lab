package lab.gabon.service

import lab.gabon.model.AppError
import lab.gabon.model.AppException
import lab.gabon.model.BUCKET_VIDEOS
import lab.gabon.model.PlayType
import lab.gabon.model.VideoStatus
import lab.gabon.repository.PlayRecordRepo
import lab.gabon.repository.VideoListRow
import lab.gabon.repository.VideoRepo
import lab.gabon.repository.VideoRow

data class PresignResult(
    val uploadUrl: String,
    val fileUrl: String,
    val s3Key: String,
)

data class VideoDetail(
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
    val createdAt: kotlinx.datetime.Instant,
    val uploaderName: String?,
    val uploaderAvatar: String?,
    val isLiked: Boolean,
)

class VideoService(
    private val videoRepo: VideoRepo,
    private val playRecordRepo: PlayRecordRepo,
    private val storageService: StorageService,
) {
    suspend fun presignUpload(
        customerId: Long,
        fileName: String,
        contentType: String,
    ): PresignResult {
        val ext = fileName.substringAfterLast('.', "bin")
        val s3Key = "$customerId/${java.util.UUID.randomUUID()}.$ext"
        val uploadUrl = storageService.presignUpload(BUCKET_VIDEOS, s3Key, contentType)
        val fileUrl = storageService.buildPublicUrl(BUCKET_VIDEOS, s3Key)
        return PresignResult(uploadUrl = uploadUrl, fileUrl = fileUrl, s3Key = s3Key)
    }

    suspend fun confirmUpload(
        customerId: Long,
        s3Key: String,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        title: String?,
        description: String? = null,
    ): Long {
        val fileUrl = storageService.buildPublicUrl(BUCKET_VIDEOS, s3Key)
        return videoRepo.create(
            customerId = customerId,
            title = title,
            description = description,
            fileName = fileName,
            fileSize = fileSize,
            fileUrl = fileUrl,
            mimeType = mimeType,
            status = VideoStatus.PENDING_REVIEW.value,
        )
    }

    suspend fun listVideos(
        page: Int,
        pageSize: Int,
        keyword: String? = null,
    ): Pair<List<VideoListRow>, Long> = videoRepo.listApproved(page, pageSize, keyword)

    suspend fun listFeatured(
        page: Int,
        pageSize: Int,
    ): Pair<List<VideoListRow>, Long> = videoRepo.listFeatured(page, pageSize)

    suspend fun getDetail(
        videoId: Long,
        currentCustomerId: Long?,
    ): VideoDetail {
        val video =
            videoRepo.findById(videoId)
                ?: throw AppException(AppError.VideoNotFound())

        val isApproved = video.status == VideoStatus.APPROVED.value
        val isOwner = currentCustomerId != null && video.customerId == currentCustomerId

        if (!isApproved && !isOwner) {
            throw AppException(AppError.VideoNotApproved())
        }

        val isLiked =
            if (currentCustomerId != null) {
                videoRepo.isLikedBy(videoId, currentCustomerId)
            } else {
                false
            }

        return video.toDetail(isLiked)
    }

    suspend fun listMyVideos(
        customerId: Long,
        page: Int,
        pageSize: Int,
        status: Short? = null,
    ): Pair<List<VideoListRow>, Long> = videoRepo.listByCustomer(customerId, page, pageSize, status)

    suspend fun listUserVideos(
        userId: Long,
        page: Int,
        pageSize: Int,
    ): Pair<List<VideoListRow>, Long> = videoRepo.listApprovedByUser(userId, page, pageSize)

    suspend fun likeVideo(
        videoId: Long,
        customerId: Long,
    ) {
        val video =
            videoRepo.findById(videoId)
                ?: throw AppException(AppError.VideoNotFound())
        if (video.status != VideoStatus.APPROVED.value) {
            throw AppException(AppError.VideoNotApproved())
        }
        videoRepo.likeVideo(videoId, customerId)
    }

    suspend fun unlikeVideo(
        videoId: Long,
        customerId: Long,
    ) {
        videoRepo.unlikeVideo(videoId, customerId)
    }

    suspend fun deleteVideo(
        videoId: Long,
        customerId: Long,
    ) {
        val deleted = videoRepo.softDelete(videoId, customerId)
        if (!deleted) {
            throw AppException(AppError.VideoNotFound())
        }
    }

    suspend fun recordPlayClick(
        videoId: Long,
        customerId: Long?,
        ipAddress: String?,
    ) {
        videoRepo.incrementTotalClicks(videoId)
        playRecordRepo.create(
            videoId = videoId,
            customerId = customerId,
            playType = PlayType.CLICK.value,
            ipAddress = ipAddress,
        )
    }

    suspend fun recordValidPlay(
        videoId: Long,
        customerId: Long?,
        ipAddress: String?,
    ) {
        videoRepo.incrementValidClicks(videoId)
        playRecordRepo.create(
            videoId = videoId,
            customerId = customerId,
            playType = PlayType.VALID_PLAY.value,
            ipAddress = ipAddress,
        )
    }

    private fun VideoRow.toDetail(isLiked: Boolean): VideoDetail =
        VideoDetail(
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
            createdAt = createdAt,
            uploaderName = uploaderName,
            uploaderAvatar = uploaderAvatar,
            isLiked = isLiked,
        )
}
