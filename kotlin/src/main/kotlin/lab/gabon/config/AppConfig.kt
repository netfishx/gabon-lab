package lab.gabon.config

import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class JwtConfig(
    val customerSecret: String,
    val adminSecret: String,
    val customerAccessTtl: Duration,
    val customerRefreshTtl: Duration,
    val adminAccessTtl: Duration,
    val adminRefreshTtl: Duration,
    val currentKid: String,
)

data class S3Config(
    val endpoint: String,
    val region: String,
    val accessKey: String,
    val secretKey: String,
    val bucketVideos: String,
    val bucketAvatars: String,
)

data class AppConfig(
    val port: Int,
    val databaseUrl: String,
    val redisUrl: String,
    val jwt: JwtConfig,
    val s3: S3Config,
) {
    companion object {
        fun load(): AppConfig {
            val env = loadEnvFile()

            fun get(key: String): String = env[key] ?: System.getenv(key) ?: error("Missing required env: $key")

            fun get(
                key: String,
                default: String,
            ): String = env[key] ?: System.getenv(key) ?: default

            return AppConfig(
                port = get("KOTLIN_PORT", "8090").toInt(),
                databaseUrl = get("DATABASE_URL"),
                redisUrl = get("REDIS_URL", "redis://localhost:6379/0"),
                jwt =
                    JwtConfig(
                        customerSecret = get("JWT_CUSTOMER_SECRET"),
                        adminSecret = get("JWT_ADMIN_SECRET"),
                        customerAccessTtl = parseDuration(get("JWT_CUSTOMER_ACCESS_TTL", "15m")),
                        customerRefreshTtl = parseDuration(get("JWT_CUSTOMER_REFRESH_TTL", "168h")),
                        adminAccessTtl = parseDuration(get("JWT_ADMIN_ACCESS_TTL", "15m")),
                        adminRefreshTtl = parseDuration(get("JWT_ADMIN_REFRESH_TTL", "168h")),
                        currentKid = get("JWT_CURRENT_KID"),
                    ),
                s3 =
                    S3Config(
                        endpoint = get("S3_ENDPOINT"),
                        region = get("S3_REGION"),
                        accessKey = get("S3_ACCESS_KEY"),
                        secretKey = get("S3_SECRET_KEY"),
                        bucketVideos = get("S3_BUCKET_VIDEOS"),
                        bucketAvatars = get("S3_BUCKET_AVATARS"),
                    ),
            )
        }

        private fun loadEnvFile(): Map<String, String> {
            val envFile = File("../.env")
            if (!envFile.exists()) return emptyMap()

            return envFile
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val idx = line.indexOf('=')
                    if (idx <= 0) return@mapNotNull null
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim().removeSurrounding("\"")
                    key to value
                }.toMap()
        }

        /** Parse Go-style duration strings: "15m", "168h", "30s", "1h30m" */
        private fun parseDuration(value: String): Duration {
            val pattern = Regex("""(\d+)([hms])""")
            val matches = pattern.findAll(value).toList()
            require(matches.isNotEmpty()) { "Invalid duration format: $value" }

            return matches.fold(Duration.ZERO) { acc, match ->
                val amount = match.groupValues[1].toLong()
                val unit = match.groupValues[2]
                acc +
                    when (unit) {
                        "h" -> amount.hours
                        "m" -> amount.minutes
                        "s" -> amount.seconds
                        else -> error("Unknown duration unit: $unit")
                    }
            }
        }
    }
}
