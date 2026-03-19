package lab.gabon.service

import io.mockk.mockk
import lab.gabon.model.TaskType
import lab.gabon.repository.SignInRepo
import lab.gabon.repository.TaskRepo
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskServiceTest {

    private val taskRepo = mockk<TaskRepo>()
    private val signInRepo = mockk<SignInRepo>()
    private val taskService = TaskService(taskRepo, signInRepo)

    // =====================================================
    // Period Key Generation — Asia/Shanghai timezone
    // =====================================================

    @Test
    fun `daily period key - 2026-03-19 08_00 Shanghai`() {
        // 2026-03-19 08:00:00 Asia/Shanghai = 2026-03-19 00:00:00 UTC
        val instant = ZonedDateTime.of(2026, 3, 19, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()
        val key = taskService.generatePeriodKey(TaskType.DAILY.value, instant)
        assertEquals("2026-03-19", key)
    }

    @Test
    fun `weekly period key - 2026-03-19 08_00 Shanghai = W12`() {
        val instant = ZonedDateTime.of(2026, 3, 19, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()
        val key = taskService.generatePeriodKey(TaskType.WEEKLY.value, instant)
        assertEquals("2026-W12", key)
    }

    @Test
    fun `monthly period key - 2026-03-19 08_00 Shanghai`() {
        val instant = ZonedDateTime.of(2026, 3, 19, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()
        val key = taskService.generatePeriodKey(TaskType.MONTHLY.value, instant)
        assertEquals("2026-03", key)
    }

    @Test
    fun `weekly period key - 2026-01-01 00_30 Shanghai = W01`() {
        // 2026-01-01 00:30:00 Asia/Shanghai = 2025-12-31 16:30:00 UTC
        val instant = ZonedDateTime.of(2025, 12, 31, 16, 30, 0, 0, ZoneOffset.UTC).toInstant()
        val key = taskService.generatePeriodKey(TaskType.WEEKLY.value, instant)
        assertEquals("2026-W01", key)
    }

    @Test
    fun `UTC 2025-12-31 23_59_59 is 2026-01-01 in Shanghai`() {
        // 2025-12-31 23:59:59 UTC = 2026-01-01 07:59:59 Asia/Shanghai
        val instant = ZonedDateTime.of(2025, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC).toInstant()
        val key = taskService.generatePeriodKey(TaskType.DAILY.value, instant)
        assertEquals("2026-01-01", key)
    }
}
