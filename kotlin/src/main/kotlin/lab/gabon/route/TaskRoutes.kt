package lab.gabon.route

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import lab.gabon.model.JsonData
import lab.gabon.plugin.customerPrincipal
import lab.gabon.service.TaskItem
import lab.gabon.service.TaskService

// -- Response DTOs --

@Serializable
data class TaskItemDto(
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

@Serializable
data class ClaimResultDto(
    val diamonds: Int,
)

@Serializable
data class SignInResultDto(
    val diamonds: Int,
)

// -- Route Registration --

fun Route.taskRoutes(taskService: TaskService) {
    authenticate("customer") {
        get("/tasks") {
            val principal = call.customerPrincipal()
            val taskType = call.queryParameters["task_type"]?.toShortOrNull()
            val taskStatus = call.queryParameters["task_status"]?.toShortOrNull()

            val items =
                taskService.listTasks(
                    customerId = principal.customerId,
                    taskType = taskType,
                    taskStatus = taskStatus,
                )
            call.respond(HttpStatusCode.OK, JsonData.ok(items.map { it.toDto() }))
        }

        post("/tasks/{progressId}/claim") {
            val principal = call.customerPrincipal()
            val progressId =
                call.pathParameters["progressId"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, JsonData.error(400, "invalid progress id"))

            val diamonds = taskService.claimReward(principal.customerId, progressId)
            call.respond(HttpStatusCode.OK, JsonData.ok(ClaimResultDto(diamonds = diamonds)))
        }

        post("/activity/sign-in") {
            val principal = call.customerPrincipal()
            val result = taskService.signIn(principal.customerId)
            call.respond(HttpStatusCode.OK, JsonData.ok(SignInResultDto(diamonds = result.diamonds)))
        }
    }
}

// -- Extension mappers --

private fun TaskItem.toDto(): TaskItemDto =
    TaskItemDto(
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
