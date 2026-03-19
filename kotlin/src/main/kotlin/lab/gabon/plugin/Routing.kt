package lab.gabon.plugin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
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
import lab.gabon.service.ReportService
import lab.gabon.service.SocialService
import lab.gabon.service.TaskService
import lab.gabon.service.UserService
import lab.gabon.service.VideoService

private const val AUTH_RATE_LIMIT = 20
private const val PUBLIC_RATE_LIMIT = 120
private const val USER_RATE_LIMIT = 200
private const val ADMIN_RATE_LIMIT = 200

fun Application.configureRouting(
    authService: AuthService,
    socialService: SocialService,
    customerRepo: CustomerRepo,
    videoService: VideoService? = null,
    adminService: AdminService? = null,
    userService: UserService? = null,
    taskService: TaskService? = null,
    reportService: ReportService? = null,
    rateLimiter: RateLimiter? = null,
) {
    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, JsonData.ok("ok"))
        }

        route("/api/v1") {
            if (rateLimiter != null) {
                // Auth routes: 20/min by IP
                rateLimit(rateLimiter, RateLimitConfig("auth", AUTH_RATE_LIMIT, keyType = KeyType.IP)) {
                    authRoutes(authService)
                }

                // Public routes: 120/min by IP
                rateLimit(rateLimiter, RateLimitConfig("pub", PUBLIC_RATE_LIMIT, keyType = KeyType.IP)) {
                    socialRoutes(socialService, customerRepo)
                    if (videoService != null) {
                        videoRoutes(videoService)
                        userVideoRoutes(videoService)
                    }
                }

                // User routes: 200/min by customer_id
                rateLimit(rateLimiter, RateLimitConfig("user", USER_RATE_LIMIT, keyType = KeyType.CUSTOMER_ID)) {
                    if (userService != null) {
                        userRoutes(userService)
                    }
                    if (taskService != null) {
                        taskRoutes(taskService)
                    }
                }
            } else {
                // No rate limiter (e.g. in tests that don't need it)
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
        }

        if (adminService != null) {
            if (rateLimiter != null) {
                // Admin routes: 200/min by admin_id
                rateLimit(rateLimiter, RateLimitConfig("admin", ADMIN_RATE_LIMIT, keyType = KeyType.ADMIN_ID)) {
                    adminRoutes(adminService, reportService)
                }
            } else {
                adminRoutes(adminService, reportService)
            }
        }
    }
}
