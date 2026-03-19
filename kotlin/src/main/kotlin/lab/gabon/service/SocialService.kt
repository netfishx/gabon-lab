package lab.gabon.service

import lab.gabon.model.AppError
import lab.gabon.model.AppException
import lab.gabon.repository.CustomerRepo
import lab.gabon.repository.FollowUserRow
import lab.gabon.repository.SocialRepo

class SocialService(
    private val socialRepo: SocialRepo,
    private val customerRepo: CustomerRepo,
) {
    suspend fun follow(
        followerId: Long,
        followedId: Long,
    ) {
        if (followerId == followedId) {
            throw AppException(AppError.CannotFollowSelf())
        }

        customerRepo.findById(followedId)
            ?: throw AppException(AppError.NotFound("user not found"))

        val inserted = socialRepo.follow(followerId, followedId)
        if (!inserted) {
            throw AppException(AppError.AlreadyFollowing())
        }
    }

    suspend fun unfollow(
        followerId: Long,
        followedId: Long,
    ) {
        if (followerId == followedId) {
            throw AppException(AppError.CannotFollowSelf())
        }

        val deleted = socialRepo.unfollow(followerId, followedId)
        if (!deleted) {
            throw AppException(AppError.NotFollowing())
        }
    }

    suspend fun getFollowStatus(
        viewerId: Long?,
        targetId: Long,
    ): Int = socialRepo.getFollowStatus(viewerId, targetId)

    suspend fun getFollowing(
        userId: Long,
        page: Int,
        pageSize: Int,
        viewerId: Long?,
    ): Pair<List<FollowUserRow>, Long> = socialRepo.listFollowing(userId, page, pageSize, viewerId)

    suspend fun getFollowers(
        userId: Long,
        page: Int,
        pageSize: Int,
        viewerId: Long?,
    ): Pair<List<FollowUserRow>, Long> = socialRepo.listFollowers(userId, page, pageSize, viewerId)

    suspend fun getFollowingCount(userId: Long): Long = socialRepo.getFollowingCount(userId)

    suspend fun getFollowerCount(userId: Long): Long = socialRepo.getFollowerCount(userId)
}
