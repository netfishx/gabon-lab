package lab.gabon.service

import lab.gabon.model.AppError
import lab.gabon.model.AppException
import lab.gabon.model.BUCKET_AVATARS
import lab.gabon.repository.CustomerRepo
import lab.gabon.repository.CustomerRow

data class MyProfile(
    val id: Long,
    val username: String,
    val name: String?,
    val phone: String?,
    val email: String?,
    val avatarUrl: String?,
    val signature: String?,
    val isVip: Boolean,
    val diamondBalance: Long,
    val lastLoginAt: String?,
    val createdAt: String,
)

data class AvatarPresignResult(
    val uploadUrl: String,
    val avatarUrl: String,
)

class UserService(
    private val customerRepo: CustomerRepo,
    private val storageService: StorageService,
) {
    suspend fun getMyProfile(customerId: Long): MyProfile {
        val customer =
            customerRepo.findById(customerId)
                ?: throw AppException(AppError.NotFound("customer not found"))
        return customer.toMyProfile()
    }

    suspend fun updateProfile(
        customerId: Long,
        name: String?,
        phone: String?,
        email: String?,
        signature: String?,
    ): MyProfile {
        val updated =
            customerRepo.updateProfile(customerId, name, phone, email, signature)
                ?: throw AppException(AppError.NotFound("customer not found"))
        return updated.toMyProfile()
    }

    suspend fun presignAvatarUpload(
        customerId: Long,
        fileName: String,
        contentType: String,
    ): AvatarPresignResult {
        val s3Key = storageService.generateKey("avatars", customerId, fileName)
        val uploadUrl = storageService.presignUpload(BUCKET_AVATARS, s3Key, contentType)
        val avatarUrl = storageService.buildPublicUrl(BUCKET_AVATARS, s3Key)
        return AvatarPresignResult(uploadUrl = uploadUrl, avatarUrl = avatarUrl)
    }

    suspend fun confirmAvatarUpload(
        customerId: Long,
        avatarUrl: String,
    ) {
        customerRepo.updateAvatarUrl(customerId, avatarUrl)
    }

    private fun CustomerRow.toMyProfile(): MyProfile =
        MyProfile(
            id = id,
            username = username,
            name = name,
            phone = phone,
            email = email,
            avatarUrl = avatarUrl,
            signature = signature,
            isVip = isVip,
            diamondBalance = diamondBalance,
            lastLoginAt = lastLoginAt?.toString(),
            createdAt = createdAt.toString(),
        )
}
