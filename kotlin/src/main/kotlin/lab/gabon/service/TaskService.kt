package lab.gabon.service

import lab.gabon.model.TaskType
import lab.gabon.repository.SignInRepo
import lab.gabon.repository.TaskRepo
import lab.gabon.repository.TaskWithProgressRow
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.IsoFields

data class TaskItem(
    val taskId: Long,
    val taskCode: String,
    val taskName: String,
    val taskType: Short,
    val targetCount: Int,
    val rewardDiamonds: Int,
    val progressId: Long,
    val currentCount: Int,
    val taskStatus: Short,
)

data class SignInResult(
    val diamonds: Int,
    val periodKey: String,
)

class TaskService(
    private val taskRepo: TaskRepo,
    private val signInRepo: SignInRepo,
) {
    companion object {
        val SHANGHAI_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        const val SIGN_IN_REWARD = 1
    }

    suspend fun listTasks(
        customerId: Long,
        taskType: Short? = null,
        taskStatus: Short? = null,
        now: Instant = Instant.now(),
    ): List<TaskItem> {
        val definitions = taskRepo.findActiveTaskDefinitions(taskType)

        // Upsert progress for each definition with its matching period key
        for (def in definitions) {
            val periodKey = generatePeriodKey(def.taskType, now)
            taskRepo.upsertProgress(
                customerId = customerId,
                taskId = def.id,
                periodKey = periodKey,
                targetCount = def.targetCount,
                rewardDiamonds = def.rewardDiamonds,
            )
        }

        // Collect all period keys for the query (daily, weekly, monthly)
        // We query for each task type's period key
        val results = mutableListOf<TaskWithProgressRow>()
        val taskTypes = definitions.map { it.taskType }.distinct()

        for (tt in taskTypes) {
            val periodKey = generatePeriodKey(tt, now)
            val filtered =
                taskRepo.listProgressWithTasks(
                    customerId = customerId,
                    periodKey = periodKey,
                    taskType = tt,
                    taskStatus = taskStatus,
                )
            results.addAll(filtered)
        }

        return results.map { it.toTaskItem() }
    }

    suspend fun claimReward(
        customerId: Long,
        progressId: Long,
    ): Int = taskRepo.claimReward(progressId, customerId)

    suspend fun signIn(
        customerId: Long,
        now: Instant = Instant.now(),
    ): SignInResult {
        val periodKey = generatePeriodKey(TaskType.DAILY.value, now)
        signInRepo.signIn(customerId, periodKey, SIGN_IN_REWARD)
        return SignInResult(diamonds = SIGN_IN_REWARD, periodKey = periodKey)
    }

    suspend fun incrementWatchProgress(
        customerId: Long,
        now: Instant = Instant.now(),
    ) {
        // Find the daily watch task (category=1) progress for today
        val periodKey = generatePeriodKey(TaskType.DAILY.value, now)
        val definitions = taskRepo.findActiveTaskDefinitions(TaskType.DAILY.value)
        val watchTask = definitions.find { it.taskCategory.toInt() == 1 } ?: return

        val progress =
            taskRepo.findProgressByCustomerAndTask(
                customerId = customerId,
                taskId = watchTask.id,
                periodKey = periodKey,
            ) ?: return

        if (progress.taskStatus.toInt() == 1) { // IN_PROGRESS
            taskRepo.incrementProgress(progress.id)
        }
    }

    fun generatePeriodKey(
        taskType: Short,
        instant: Instant,
    ): String {
        val zdt: ZonedDateTime = instant.atZone(SHANGHAI_ZONE)
        return when (taskType) {
            TaskType.DAILY.value -> zdt.toLocalDate().toString() // "2026-03-19"
            TaskType.WEEKLY.value -> {
                val year = zdt.get(IsoFields.WEEK_BASED_YEAR)
                val week = zdt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                "%d-W%02d".format(year, week) // "2026-W12"
            }
            TaskType.MONTHLY.value -> "%d-%02d".format(zdt.year, zdt.monthValue) // "2026-03"
            else -> throw IllegalArgumentException("Unknown task type: $taskType")
        }
    }

    private fun TaskWithProgressRow.toTaskItem(): TaskItem =
        TaskItem(
            taskId = taskId,
            taskCode = taskCode,
            taskName = taskName,
            taskType = taskType,
            targetCount = targetCount,
            rewardDiamonds = rewardDiamonds,
            progressId = progressId,
            currentCount = currentCount,
            taskStatus = taskStatus,
        )
}
