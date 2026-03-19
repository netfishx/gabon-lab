package lab.gabon

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import lab.gabon.config.AppConfig
import lab.gabon.config.createRedis
import lab.gabon.config.initDatabase
import lab.gabon.config.shutdownRedis
import lab.gabon.plugin.configureAuthentication
import lab.gabon.plugin.configureErrorHandling
import lab.gabon.plugin.configureRouting
import lab.gabon.plugin.configureSerialization
import lab.gabon.repository.CustomerRepo
import lab.gabon.service.AuthService
import lab.gabon.service.JwtService
import lab.gabon.service.RedisTokenStore
import lab.gabon.service.StorageService

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun main() {
    val config = AppConfig.load()

    // Infrastructure
    initDatabase(config)
    val redis = createRedis(config.redisUrl)

    // Services
    val jwtService = JwtService(config.jwt)
    val tokenStore = RedisTokenStore(redis.commands)
    val storageService = StorageService(config.s3)
    val customerRepo = CustomerRepo()
    val authService = AuthService(customerRepo, jwtService, tokenStore)

    embeddedServer(Netty, port = config.port) {
        configureSerialization()
        configureErrorHandling()
        configureAuthentication(jwtService, tokenStore)
        configureRouting(authService)

        monitor.subscribe(ApplicationStopped) {
            storageService.close()
            shutdownRedis(redis)
        }
    }.start(wait = true)
}
