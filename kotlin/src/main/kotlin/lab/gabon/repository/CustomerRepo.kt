package lab.gabon.repository

import kotlinx.datetime.Instant
import lab.gabon.config.dbQuery
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

data class CustomerRow(
    val id: Long,
    val username: String,
    val passwordHash: String,
    val name: String?,
    val phone: String?,
    val email: String?,
    val avatarUrl: String?,
    val signature: String?,
    val isVip: Boolean,
    val diamondBalance: Long,
    val lastLoginAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

class CustomerRepo {

    suspend fun create(username: String, passwordHash: String): Long = dbQuery {
        Customers.insertAndGetId {
            it[Customers.username] = username
            it[Customers.passwordHash] = passwordHash
        }.value
    }

    suspend fun findByUsername(username: String): CustomerRow? = dbQuery {
        Customers
            .selectAll()
            .where {
                (Customers.username.lowerCase() eq username.lowercase()) and
                    Customers.deletedAt.isNull()
            }
            .singleOrNull()
            ?.toCustomerRow()
    }

    suspend fun findById(id: Long): CustomerRow? = dbQuery {
        Customers
            .selectAll()
            .where {
                (Customers.id eq id) and Customers.deletedAt.isNull()
            }
            .singleOrNull()
            ?.toCustomerRow()
    }

    suspend fun updatePassword(id: Long, newHash: String): Unit = dbQuery {
        Customers.update({ Customers.id eq id }) {
            it[passwordHash] = newHash
            it[updatedAt] = CurrentTimestampWithTimeZone
        }
    }

    suspend fun updateLastLogin(id: Long): Unit = dbQuery {
        Customers.update({ Customers.id eq id }) {
            it[lastLoginAt] = CurrentTimestampWithTimeZone
            it[updatedAt] = CurrentTimestampWithTimeZone
        }
    }

    private fun ResultRow.toCustomerRow(): CustomerRow = CustomerRow(
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

    private fun OffsetDateTime.toKotlinInstant(): Instant =
        Instant.fromEpochSeconds(toEpochSecond(), nano)
}
