package lab.gabon.repository

import kotlinx.datetime.Instant
import lab.gabon.config.dbQuery
import lab.gabon.model.AppError
import lab.gabon.model.AppException
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.time.OffsetDateTime

data class SignInRecordRow(
    val id: Long,
    val customerId: Long,
    val periodKey: String,
    val rewardDiamonds: Int,
    val createdAt: Instant,
)

class SignInRepo {
    suspend fun signIn(
        customerId: Long,
        periodKey: String,
        diamonds: Int,
    ): SignInRecordRow =
        dbQuery {
            val result =
                CustomerSignInRecords.insertIgnore {
                    it[CustomerSignInRecords.customerId] = customerId
                    it[CustomerSignInRecords.periodKey] = periodKey
                    it[CustomerSignInRecords.rewardDiamonds] = diamonds
                }

            if (result.insertedCount == 0) {
                throw AppException(AppError.AlreadySignedIn())
            }

            // Credit diamonds
            val creditSql =
                """
                UPDATE customers SET diamond_balance = diamond_balance + ?, updated_at = NOW() WHERE id = ?
                """.trimIndent()
            val jdbcConn = TransactionManager.current().connection.connection as java.sql.Connection
            jdbcConn.prepareStatement(creditSql).use { stmt ->
                stmt.setInt(1, diamonds)
                stmt.setLong(2, customerId)
                stmt.executeUpdate()
            }

            // Return the record
            CustomerSignInRecords
                .selectAll()
                .where {
                    (CustomerSignInRecords.customerId eq customerId) and
                        (CustomerSignInRecords.periodKey eq periodKey)
                }.single()
                .toSignInRecordRow()
        }

    private fun ResultRow.toSignInRecordRow(): SignInRecordRow =
        SignInRecordRow(
            id = this[CustomerSignInRecords.id].value,
            customerId = this[CustomerSignInRecords.customerId].value,
            periodKey = this[CustomerSignInRecords.periodKey],
            rewardDiamonds = this[CustomerSignInRecords.rewardDiamonds],
            createdAt = this[CustomerSignInRecords.createdAt].toKotlinInstant(),
        )

    private fun OffsetDateTime.toKotlinInstant(): Instant = Instant.fromEpochSeconds(toEpochSecond(), nano)
}
