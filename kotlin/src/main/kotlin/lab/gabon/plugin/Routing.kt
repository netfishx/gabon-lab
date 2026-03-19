package lab.gabon.plugin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lab.gabon.model.JsonData
import lab.gabon.repository.CustomerRepo
import lab.gabon.route.adminRoutes
import lab.gabon.route.authRoutes
import lab.gabon.route.socialRoutes
import lab.gabon.route.taskRoutes
import lab.gabon.route.userRoutes
import lab.gabon.route.userVideoRoutes
import lab.gabon.route.videoRoutes
import lab.gabon.service.AdminService
import lab.gabon.service.AuthService
import lab.gabon.service.SocialService
import lab.gabon.service.TaskService
import lab.gabon.service.UserService
import lab.gabon.service.VideoService

fun Application.configureRouting(
    authService: AuthService,
    socialService: SocialService,
    customerRepo: CustomerRepo,
    videoService: VideoService? = null,
    adminService: AdminService? = null,
    userService: UserService? = null,
    taskService: TaskService? = null,
) {
    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, JsonData.ok("ok"))
        }

        route("/api/v1") {
            authRoutes(authService)
            socialRoutes(socialService, customerRepo)
            if (videoService != null) {
                videoRoutes(videoService)
                userVideoRoutes(videoService)
            }
            if (userService != null) {
                userRoutes(userService)
            }
            if (taskService != null) {
                taskRoutes(taskService)
            }
        }

        if (adminService != null) {
            adminRoutes(adminService)
        }
    }
}
