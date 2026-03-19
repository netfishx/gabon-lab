package lab.gabon.service

import at.favre.lib.crypto.bcrypt.BCrypt
import lab.gabon.model.AdminRole
import lab.gabon.model.AdminStatus
import lab.gabon.model.AppError
import lab.gabon.model.AppException
import lab.gabon.model.VideoStatus
import lab.gabon.repository.AdminUserRepo
import lab.gabon.repository.AdminUserRow
import lab.gabon.repository.AdminVideoListRow
import lab.gabon.repository.AdminVideoRepo
import lab.gabon.repository.AdminVideoRow
import lab.gabon.repository.CustomerRepo
import lab.gabon.repository.CustomerRow

class AdminService(
    private val adminUserRepo: AdminUserRepo,
    private val adminVideoRepo: AdminVideoRepo,
    private val customerRepo: CustomerRepo,
    private val jwtService: JwtService,
    private val tokenStore: RedisTokenStore,
) {
    // ── Admin Auth ─────────────────────────────────────────────

    suspend fun login(
        username: String,
        password: String,
    ): TokenResponse {
        val admin =
            adminUserRepo.findByUsername(username)
                ?: throw AppException(AppError.InvalidCredentials())

        if (admin.status != AdminStatus.ACTIVE.value) {
            throw AppException(AppError.Forbidden("account is disabled"))
        }

        if (!verifyPassword(password, admin.passwordHash)) {
            throw AppException(AppError.InvalidCredentials())
        }

        adminUserRepo.updateLastLogin(admin.id)

        val roleName = AdminRole.fromValue(admin.role).name.lowercase()
        val tokenPair = jwtService.generateAdminTokens(admin.id, roleName)
        tokenStore.setFamily(
            familyId = tokenPair.familyId,
            userId = admin.id,
            currentJti = tokenPair.refreshJti,
            ttlSeconds = jwtService.config.adminRefreshTtl.inWholeSeconds,
        )

        return TokenResponse(tokenPair.accessToken, tokenPair.refreshToken)
    }

    suspend fun refresh(refreshTokenStr: String): TokenResponse {
        val claims = jwtService.parseAdminToken(refreshTokenStr)

        if (claims.tokenType != "refresh") {
            throw AppException(AppError.TokenInvalid("not a refresh token"))
        }

        val role = claims.role ?: throw AppException(AppError.TokenInvalid("missing role"))
        val newTokenPair = jwtService.generateAdminTokens(claims.userId, role)

        val casResult =
            tokenStore.casFamily(
                familyId = claims.familyId,
                expectedJti = claims.jti,
                newJti = newTokenPair.refreshJti,
            )

        return when (casResult) {
            is CasResult.Success ->
                TokenResponse(newTokenPair.accessToken, newTokenPair.refreshToken)
            is CasResult.Missing ->
                throw AppException(AppError.TokenInvalid("token family expired or revoked"))
            is CasResult.Conflict ->
                throw AppException(AppError.TokenInvalid("token reuse detected, family revoked"))
        }
    }

    @Suppress("UnusedParameter")
    suspend fun logout(
        adminId: Long,
        jti: String,
        familyId: String,
    ) {
        val remainingSeconds = jwtService.config.adminAccessTtl.inWholeSeconds
        tokenStore.setBlacklist(jti, remainingSeconds)
        tokenStore.deleteFamily(familyId)
    }

    suspend fun getMe(adminId: Long): AdminUserRow =
        adminUserRepo.findById(adminId)
            ?: throw AppException(AppError.NotFound("admin not found"))

    // ── Admin CRUD ─────────────────────────────────────────────

    suspend fun createAdmin(
        currentRole: String,
        username: String,
        password: String,
        role: Short,
        fullName: String? = null,
    ): Long {
        requireSuperadmin(currentRole)

        val existing = adminUserRepo.findByUsername(username)
        if (existing != null) {
            throw AppException(AppError.UsernameExists())
        }

        val hash = hashPassword(password)
        return adminUserRepo.create(username, hash, role, fullName)
    }

    suspend fun listAdmins(
        page: Int,
        pageSize: Int,
        username: String? = null,
        role: Short? = null,
        status: Short? = null,
    ): Pair<List<AdminUserRow>, Long> = adminUserRepo.listAdmins(page, pageSize, username, role, status)

    suspend fun getAdmin(id: Long): AdminUserRow =
        adminUserRepo.findById(id)
            ?: throw AppException(AppError.NotFound("admin not found"))

    suspend fun updateAdmin(
        currentRole: String,
        targetId: Long,
        role: Short? = null,
        fullName: String? = null,
        phone: String? = null,
        status: Short? = null,
    ) {
        requireSuperadmin(currentRole)

        val updated = adminUserRepo.updateAdmin(targetId, role, fullName, phone, status)
        if (!updated) {
            throw AppException(AppError.NotFound("admin not found"))
        }
    }

    suspend fun deleteAdmin(
        currentRole: String,
        currentAdminId: Long,
        targetId: Long,
    ) {
        requireSuperadmin(currentRole)

        if (currentAdminId == targetId) {
            throw AppException(AppError.BadRequest("cannot delete yourself"))
        }

        val deleted = adminUserRepo.softDelete(targetId)
        if (!deleted) {
            throw AppException(AppError.NotFound("admin not found"))
        }
    }

    suspend fun changeAdminPassword(
        currentAdminId: Long,
        currentRole: String,
        targetId: Long,
        newPassword: String,
    ) {
        if (currentAdminId != targetId) {
            if (currentRole != AdminRole.SUPERADMIN.name.lowercase()) {
                throw AppException(AppError.Forbidden("cannot change other admin's password"))
            }
        }

        val hash = hashPassword(newPassword)
        adminUserRepo.updatePassword(targetId, hash)
    }

    // ── Video Management ───────────────────────────────────────

    suspend fun listVideosAdmin(
        page: Int,
        pageSize: Int,
        status: Short? = null,
        authorName: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        isVip: Boolean? = null,
    ): Pair<List<AdminVideoListRow>, Long> =
        adminVideoRepo.listVideosAdmin(
            page,
            pageSize,
            status,
            authorName,
            startDate,
            endDate,
            isVip,
        )

    suspend fun getVideoDetailAdmin(videoId: Long): AdminVideoRow =
        adminVideoRepo.getVideoDetailAdmin(videoId)
            ?: throw AppException(AppError.VideoNotFound())

    suspend fun reviewVideo(
        videoId: Long,
        adminId: Long,
        status: Short,
        reviewNotes: String?,
    ) {
        if (status != VideoStatus.APPROVED.value && status != VideoStatus.REJECTED.value) {
            throw AppException(AppError.BadRequest("status must be 4 (approved) or 5 (rejected)"))
        }

        val updated = adminVideoRepo.reviewVideo(videoId, adminId, status, reviewNotes)
        if (!updated) {
            throw AppException(AppError.VideoNotFound())
        }
    }

    suspend fun adminDeleteVideo(videoId: Long) {
        val deleted = adminVideoRepo.adminDeleteVideo(videoId)
        if (!deleted) {
            throw AppException(AppError.VideoNotFound())
        }
    }

    // ── Customer Management ────────────────────────────────────

    suspend fun listCustomers(
        page: Int,
        pageSize: Int,
        name: String? = null,
        isVip: Boolean? = null,
    ): Pair<List<CustomerRow>, Long> = adminVideoRepo.listCustomers(page, pageSize, name, isVip)

    suspend fun resetCustomerPassword(
        customerId: Long,
        newPassword: String,
    ) {
        customerRepo.findById(customerId)
            ?: throw AppException(AppError.NotFound("customer not found"))

        val hash = hashPassword(newPassword)
        customerRepo.updatePassword(customerId, hash)
    }

    // ── Helpers ────────────────────────────────────────────────

    private fun requireSuperadmin(role: String) {
        if (role != AdminRole.SUPERADMIN.name.lowercase()) {
            throw AppException(AppError.Forbidden("superadmin required"))
        }
    }

    private fun hashPassword(password: String): String =
        BCrypt
            .withDefaults()
            .hashToString(BCRYPT_COST, password.toCharArray())

    private fun verifyPassword(
        password: String,
        hash: String,
    ): Boolean = BCrypt.verifyer().verify(password.toCharArray(), hash).verified

    companion object {
        private const val BCRYPT_COST = 10
    }
}
