package lab.gabon.config

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands

/**
 * Holds the Lettuce RedisClient, connection, and coroutine commands.
 * Created via [createRedis], shut down via [shutdownRedis].
 */
class RedisResources(
    val client: RedisClient,
    val connection: StatefulRedisConnection<String, String>,
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    val commands: RedisCoroutinesCommands<String, String>,
)

/**
 * Create a Lettuce [RedisClient] from the given Redis URL
 * (e.g. `redis://:benchpass@localhost:6379/0`), open a connection,
 * and obtain [RedisCoroutinesCommands] for suspend-friendly usage.
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun createRedis(redisUrl: String): RedisResources {
    val client = RedisClient.create(redisUrl)
    val connection = client.connect()
    val commands = connection.coroutines()
    return RedisResources(client, connection, commands)
}

/**
 * Gracefully shut down the Redis connection and client.
 * Call from Ktor's [ApplicationStopped] monitor hook.
 */
fun shutdownRedis(resources: RedisResources) {
    resources.connection.close()
    resources.client.shutdown()
}
