package lab.gabon

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import lab.gabon.config.AppConfig
import lab.gabon.config.createRedis
import lab.gabon.config.initDatabase
import lab.gabon.config.shutdownRedis
import lab.gabon.plugin.RateLimiter
import lab.gabon.plugin.configureAuthentication
import lab.gabon.plugin.configureErrorHandling
import lab.gabon.plugin.configureRouting
import lab.gabon.plugin.configureSerialization
import lab.gabon.repository.AdminUserRepo
import lab.gabon.repository.AdminVideoRepo
import lab.gabon.repository.CustomerRepo
import lab.gabon.repository.PlayRecordRepo
import lab.gabon.repository.ReportRepo
import lab.gabon.repository.SignInRepo
import lab.gabon.repository.SocialRepo
import lab.gabon.repository.TaskRepo
import lab.gabon.repository.VideoRepo
import lab.gabon.service.AdminService
import lab.gabon.service.AuthService
import lab.gabon.service.JwtService
import lab.gabon.service.RedisTokenStore
import lab.gabon.service.ReportService
import lab.gabon.service.SocialService
import lab.gabon.service.StorageService
import lab.gabon.service.TaskService
import lab.gabon.service.UserService
import lab.gabon.service.VideoService

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun main() {
    val config = AppConfig.load()

    // Infrastructure
    initDatabase(config)
    val redis = createRedis(config.redisUrl)

    // Rate limiter
    val rateLimiter = RateLimiter(redis.commands)

    // Services
    val jwtService = JwtService(config.jwt)
    val tokenStore = RedisTokenStore(redis.commands)
    val storageService = StorageService(config.s3)
    val customerRepo = CustomerRepo()
    val authService = AuthService(customerRepo, jwtService, tokenStore)
    val socialRepo = SocialRepo()
    val socialService = SocialService(socialRepo, customerRepo)
    val videoRepo = VideoRepo()
    val playRecordRepo = PlayRecordRepo()
    val videoService = VideoService(videoRepo, playRecordRepo, storageService)
    val adminUserRepo = AdminUserRepo()
    val adminVideoRepo = AdminVideoRepo()
    val adminService = AdminService(adminUserRepo, adminVideoRepo, customerRepo, jwtService, tokenStore)
    val userService = UserService(customerRepo, storageService)
    val taskRepo = TaskRepo()
    val signInRepo = SignInRepo()
    val taskService = TaskService(taskRepo, signInRepo)
    val reportRepo = ReportRepo()
    val reportService = ReportService(reportRepo)

    embeddedServer(Netty, port = config.port) {
        configureSerialization()
        configureErrorHandling()
        configureAuthentication(jwtService, tokenStore)
        configureRouting(authService, socialService, customerRepo, videoService, adminService, userService, taskService, reportService, rateLimiter)

        monitor.subscribe(ApplicationStopped) {
            storageService.close()
            shutdownRedis(redis)
        }
    }.start(wait = true)
}
