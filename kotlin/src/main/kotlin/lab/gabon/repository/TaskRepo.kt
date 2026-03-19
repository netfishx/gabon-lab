package lab.gabon.repository

import kotlinx.datetime.Instant
import lab.gabon.config.dbQuery
import lab.gabon.model.AppError
import lab.gabon.model.AppException
import lab.gabon.model.TaskStatus
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.time.OffsetDateTime

data class TaskDefinitionRow(
    val id: Long,
    val taskCode: String,
    val taskName: String,
    val description: String?,
    val taskType: Short,
    val taskCategory: Short,
    val targetCount: Int,
    val rewardDiamonds: Int,
    val iconUrl: String?,
    val displayOrder: Int,
    val vipOnly: Boolean,
    val status: Short,
)

data class TaskProgressRow(
    val id: Long,
    val customerId: Long,
    val taskId: Long,
    val currentCount: Int,
    val targetCount: Int,
    val periodKey: String,
    val taskStatus: Short,
    val rewardDiamonds: Int,
    val completedAt: Instant?,
    val claimedAt: Instant?,
    val createdAt: Instant,
)

data class TaskWithProgressRow(
    val taskId: Long,
    val taskCode: String,
    val taskName: String,
    val taskType: Short,
    val taskCategory: Short,
    val targetCount: Int,
    val rewardDiamonds: Int,
    val iconUrl: String?,
    val progressId: Long,
    val currentCount: Int,
    val taskStatus: Short,
    val periodKey: String,
)

class TaskRepo {
    suspend fun findActiveTaskDefinitions(taskType: Short? = null): List<TaskDefinitionRow> =
        dbQuery {
            TaskDefinitions
                .selectAll()
                .where {
                    val base = TaskDefinitions.status eq 1.toShort()
                    if (taskType != null) base and (TaskDefinitions.taskType eq taskType) else base
                }.orderBy(TaskDefinitions.displayOrder)
                .map { it.toTaskDefinitionRow() }
        }

    suspend fun upsertProgress(
        customerId: Long,
        taskId: Long,
        periodKey: String,
        targetCount: Int,
        rewardDiamonds: Int,
    ): TaskProgressRow =
        dbQuery {
            TaskProgress.insertIgnore {
                it[TaskProgress.customerId] = customerId
                it[TaskProgress.taskId] = taskId
                it[TaskProgress.periodKey] = periodKey
                it[TaskProgress.targetCount] = targetCount
                it[TaskProgress.rewardDiamonds] = rewardDiamonds
            }
            // Return existing or newly created
            TaskProgress
                .selectAll()
                .where {
                    (TaskProgress.customerId eq customerId) and
                        (TaskProgress.taskId eq taskId) and
                        (TaskProgress.periodKey eq periodKey)
                }.single()
                .toTaskProgressRow()
        }

    suspend fun findProgressById(progressId: Long): TaskProgressRow? =
        dbQuery {
            TaskProgress
                .selectAll()
                .where { TaskProgress.id eq progressId }
                .singleOrNull()
                ?.toTaskProgressRow()
        }

    suspend fun findProgressByCustomerAndTask(
        customerId: Long,
        taskId: Long,
        periodKey: String,
    ): TaskProgressRow? =
        dbQuery {
            TaskProgress
                .selectAll()
                .where {
                    (TaskProgress.customerId eq customerId) and
                        (TaskProgress.taskId eq taskId) and
                        (TaskProgress.periodKey eq periodKey)
                }.singleOrNull()
                ?.toTaskProgressRow()
        }

    suspend fun listProgressWithTasks(
        customerId: Long,
        periodKey: String,
        taskType: Short? = null,
        taskStatus: Short? = null,
    ): List<TaskWithProgressRow> =
        dbQuery {
            TaskDefinitions
                .join(TaskProgress, JoinType.INNER, TaskDefinitions.id, TaskProgress.taskId)
                .selectAll()
                .where {
                    var cond =
                        (TaskProgress.customerId eq customerId) and
                            (TaskProgress.periodKey eq periodKey) and
                            (TaskDefinitions.status eq 1.toShort())
                    if (taskType != null) cond = cond and (TaskDefinitions.taskType eq taskType)
                    if (taskStatus != null) cond = cond and (TaskProgress.taskStatus eq taskStatus)
                    cond
                }.orderBy(TaskDefinitions.displayOrder)
                .map { row ->
                    TaskWithProgressRow(
                        taskId = row[TaskDefinitions.id].value,
                        taskCode = row[TaskDefinitions.taskCode],
                        taskName = row[TaskDefinitions.taskName],
                        taskType = row[TaskDefinitions.taskType],
                        taskCategory = row[TaskDefinitions.taskCategory],
                        targetCount = row[TaskProgress.targetCount],
                        rewardDiamonds = row[TaskProgress.rewardDiamonds],
                        iconUrl = row[TaskDefinitions.iconUrl],
                        progressId = row[TaskProgress.id].value,
                        currentCount = row[TaskProgress.currentCount],
                        taskStatus = row[TaskProgress.taskStatus],
                        periodKey = row[TaskProgress.periodKey],
                    )
                }
        }

    suspend fun incrementProgress(progressId: Long): Unit =
        dbQuery {
            // Atomic increment: current_count += 1, auto-complete if reaching target
            // Using CASE: task_status = CASE WHEN current_count + 1 >= target_count THEN 2 ELSE task_status END
            val sql =
                """
                UPDATE task_progress SET
                    current_count = current_count + 1,
                    task_status = CASE WHEN current_count + 1 >= target_count THEN 2 ELSE task_status END,
                    completed_at = CASE WHEN current_count + 1 >= target_count THEN NOW() ELSE completed_at END,
                    updated_at = NOW()
                WHERE id = ? AND task_status = 1
                """.trimIndent()
            val jdbcConn = TransactionManager.current().connection.connection as java.sql.Connection
            jdbcConn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, progressId)
                stmt.executeUpdate()
            }
        }

    suspend fun claimReward(
        progressId: Long,
        customerId: Long,
    ): Int =
        dbQuery {
            val jdbcConn = TransactionManager.current().connection.connection as java.sql.Connection

            // FOR UPDATE lock, verify status, update, and credit diamonds -- all in one transaction
            val lockSql =
                """
                SELECT reward_diamonds, task_status, customer_id
                FROM task_progress
                WHERE id = ? AND customer_id = ?
                FOR UPDATE
                """.trimIndent()

            var rewardDiamonds = 0
            var found = false

            jdbcConn.prepareStatement(lockSql).use { stmt ->
                stmt.setLong(1, progressId)
                stmt.setLong(2, customerId)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    found = true
                    val status = rs.getShort("task_status")
                    if (status != TaskStatus.COMPLETED.value) {
                        val msg =
                            if (status == TaskStatus.CLAIMED.value) {
                                "task already claimed"
                            } else {
                                "task is not completed"
                            }
                        throw AppException(AppError.TaskNotClaimable(msg))
                    }
                    rewardDiamonds = rs.getInt("reward_diamonds")
                }
            }

            if (!found) {
                throw AppException(AppError.TaskNotClaimable("task progress not found"))
            }

            // Update progress status to CLAIMED
            val updateSql =
                """
                UPDATE task_progress SET task_status = 3, claimed_at = NOW(), updated_at = NOW() WHERE id = ?
                """.trimIndent()
            jdbcConn.prepareStatement(updateSql).use { stmt ->
                stmt.setLong(1, progressId)
                stmt.executeUpdate()
            }

            // Credit diamonds
            val creditSql =
                """
                UPDATE customers SET diamond_balance = diamond_balance + ?, updated_at = NOW() WHERE id = ?
                """.trimIndent()
            jdbcConn.prepareStatement(creditSql).use { stmt ->
                stmt.setInt(1, rewardDiamonds)
                stmt.setLong(2, customerId)
                stmt.executeUpdate()
            }

            rewardDiamonds
        }

    private fun ResultRow.toTaskDefinitionRow(): TaskDefinitionRow =
        TaskDefinitionRow(
            id = this[TaskDefinitions.id].value,
            taskCode = this[TaskDefinitions.taskCode],
            taskName = this[TaskDefinitions.taskName],
            description = this[TaskDefinitions.description],
            taskType = this[TaskDefinitions.taskType],
            taskCategory = this[TaskDefinitions.taskCategory],
            targetCount = this[TaskDefinitions.targetCount],
            rewardDiamonds = this[TaskDefinitions.rewardDiamonds],
            iconUrl = this[TaskDefinitions.iconUrl],
            displayOrder = this[TaskDefinitions.displayOrder],
            vipOnly = this[TaskDefinitions.vipOnly],
            status = this[TaskDefinitions.status],
        )

    private fun ResultRow.toTaskProgressRow(): TaskProgressRow =
        TaskProgressRow(
            id = this[TaskProgress.id].value,
            customerId = this[TaskProgress.customerId].value,
            taskId = this[TaskProgress.taskId].value,
            currentCount = this[TaskProgress.currentCount],
            targetCount = this[TaskProgress.targetCount],
            periodKey = this[TaskProgress.periodKey],
            taskStatus = this[TaskProgress.taskStatus],
            rewardDiamonds = this[TaskProgress.rewardDiamonds],
            completedAt = this[TaskProgress.completedAt]?.toKotlinInstant(),
            claimedAt = this[TaskProgress.claimedAt]?.toKotlinInstant(),
            createdAt = this[TaskProgress.createdAt].toKotlinInstant(),
        )

    private fun OffsetDateTime.toKotlinInstant(): Instant = Instant.fromEpochSeconds(toEpochSecond(), nano)
}
