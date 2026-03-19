package lab.gabon.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.net.URI
import java.util.concurrent.Executors

val LoomDispatcher: CoroutineDispatcher =
    Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()

fun initDatabase(config: AppConfig): Database {
    val dataSource = createDataSource(config.databaseUrl)

    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate()

    return Database.connect(dataSource)
}

suspend fun <T> dbQuery(block: () -> T): T =
    withContext(LoomDispatcher) {
        transaction { block() }
    }

private fun createDataSource(databaseUrl: String): HikariDataSource {
    val uri = URI(databaseUrl)
    val userInfo = uri.userInfo?.split(":") ?: error("DATABASE_URL missing user info")
    val jdbcUrl = "jdbc:postgresql://${uri.host}:${uri.port}${uri.path}"

    return HikariDataSource(HikariConfig().apply {
        this.jdbcUrl = jdbcUrl
        username = userInfo[0]
        password = userInfo.getOrElse(1) { "" }
        maximumPoolSize = 30
        isAutoCommit = false
        connectionTimeout = 10_000
        driverClassName = "org.postgresql.Driver"
    })
}
