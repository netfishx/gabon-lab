package lab.gabon.repository

import kotlinx.datetime.Instant
import lab.gabon.config.dbQuery
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

data class AdminUserRow(
    val id: Long,
    val username: String,
    val passwordHash: String,
    val role: Short,
    val fullName: String?,
    val phone: String?,
    val avatarUrl: String?,
    val status: Short,
    val lastLoginAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

class AdminUserRepo {

    suspend fun create(
        username: String,
        passwordHash: String,
        role: Short,
        fullName: String? = null,
    ): Long = dbQuery {
        AdminUsers.insertAndGetId {
            it[AdminUsers.username] = username
            it[AdminUsers.passwordHash] = passwordHash
            it[AdminUsers.role] = role
            it[AdminUsers.fullName] = fullName
        }.value
    }

    suspend fun findByUsername(username: String): AdminUserRow? = dbQuery {
        AdminUsers
            .selectAll()
            .where {
                (AdminUsers.username.lowerCase() eq username.lowercase()) and
                    AdminUsers.deletedAt.isNull()
            }
            .singleOrNull()
            ?.toAdminUserRow()
    }

    suspend fun findById(id: Long): AdminUserRow? = dbQuery {
        AdminUsers
            .selectAll()
            .where {
                (AdminUsers.id eq id) and AdminUsers.deletedAt.isNull()
            }
            .singleOrNull()
            ?.toAdminUserRow()
    }

    suspend fun listAdmins(
        page: Int,
        pageSize: Int,
        username: String? = null,
        role: Short? = null,
        status: Short? = null,
    ): Pair<List<AdminUserRow>, Long> = dbQuery {
        var condition: Op<Boolean> = AdminUsers.deletedAt.isNull()

        if (!username.isNullOrBlank()) {
            condition = condition and
                (AdminUsers.username.lowerCase() like LikePattern("%${username.lowercase()}%"))
        }
        if (role != null) {
            condition = condition and (AdminUsers.role eq role)
        }
        if (status != null) {
            condition = condition and (AdminUsers.status eq status)
        }

        val total = AdminUsers
            .selectAll()
            .where { condition }
            .count()

        val items = AdminUsers
            .selectAll()
            .where { condition }
            .orderBy(AdminUsers.id to SortOrder.ASC)
            .limit(pageSize)
            .offset(((page - 1) * pageSize).toLong())
            .map { it.toAdminUserRow() }

        items to total
    }

    suspend fun updateAdmin(
        id: Long,
        role: Short? = null,
        fullName: String? = null,
        phone: String? = null,
        status: Short? = null,
    ): Boolean = dbQuery {
        AdminUsers.update({
            (AdminUsers.id eq id) and AdminUsers.deletedAt.isNull()
        }) {
            if (role != null) it[AdminUsers.role] = role
            if (fullName != null) it[AdminUsers.fullName] = fullName
            if (phone != null) it[AdminUsers.phone] = phone
            if (status != null) it[AdminUsers.status] = status
            it[updatedAt] = CurrentTimestampWithTimeZone
        } > 0
    }

    suspend fun softDelete(id: Long): Boolean = dbQuery {
        AdminUsers.update({
            (AdminUsers.id eq id) and AdminUsers.deletedAt.isNull()
        }) {
            it[deletedAt] = CurrentTimestampWithTimeZone
            it[updatedAt] = CurrentTimestampWithTimeZone
        } > 0
    }

    suspend fun updatePassword(id: Long, newHash: String): Unit = dbQuery {
        AdminUsers.update({ AdminUsers.id eq id }) {
            it[passwordHash] = newHash
            it[updatedAt] = CurrentTimestampWithTimeZone
        }
    }

    suspend fun updateLastLogin(id: Long): Unit = dbQuery {
        AdminUsers.update({ AdminUsers.id eq id }) {
            it[lastLoginAt] = CurrentTimestampWithTimeZone
            it[updatedAt] = CurrentTimestampWithTimeZone
        }
    }

    private fun ResultRow.toAdminUserRow(): AdminUserRow = AdminUserRow(
        id = this[AdminUsers.id].value,
        username = this[AdminUsers.username],
        passwordHash = this[AdminUsers.passwordHash],
        role = this[AdminUsers.role],
        fullName = this[AdminUsers.fullName],
        phone = this[AdminUsers.phone],
        avatarUrl = this[AdminUsers.avatarUrl],
        status = this[AdminUsers.status],
        lastLoginAt = this[AdminUsers.lastLoginAt]?.toKotlinInstant(),
        createdAt = this[AdminUsers.createdAt].toKotlinInstant(),
        updatedAt = this[AdminUsers.updatedAt].toKotlinInstant(),
    )

    private fun OffsetDateTime.toKotlinInstant(): Instant =
        Instant.fromEpochSeconds(toEpochSecond(), nano)
}
