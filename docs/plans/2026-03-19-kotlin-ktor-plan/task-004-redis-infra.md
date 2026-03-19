# Task 004: Redis infrastructure with Lettuce coroutines

**type**: setup
**depends-on**: [001]

## Description

Set up Redis connectivity using Lettuce with its Kotlin coroutine API, and implement the token store for JWT blacklisting and refresh token family tracking.

Key decisions:

- **Redis connection** (`config/Redis.kt`): create `RedisClient.create(redisUrl)` where redisUrl comes from AppConfig (e.g., `redis://:benchpass@localhost:6379`). Obtain a `StatefulRedisConnection<String, String>` and then get `RedisCoroutinesCommands<String, String>` via `.coroutines()`. This avoids blocking — Lettuce coroutine commands are natively suspend. Provide a shutdown function that closes connection and client gracefully, to be called from Application shutdown hook.

- **TokenStore** (`service/TokenStore.kt`): implement `class RedisTokenStore(private val redis: RedisCoroutinesCommands<String, String>)` with these operations:

  - `suspend fun setBlacklist(jti: String, ttl: Duration)` — SET key `token:blacklist:{jti}` with value `"1"` and EX ttl in seconds. Used when logging out or rotating refresh tokens to invalidate old access tokens.

  - `suspend fun isBlacklisted(jti: String): Boolean` — EXISTS key `token:blacklist:{jti}`, return true if > 0.

  - `suspend fun setFamily(familyId: String, userId: Long, currentJti: String, ttl: Duration)` — SET key `token:family:{familyId}` with value `"{userId}:{currentJti}"` and EX ttl. Creates a new refresh token family.

  - `suspend fun casFamily(familyId: String, expectedJti: String, newJti: String): CasResult` — execute a Lua script atomically:
    ```
    local v = redis.call('GET', KEYS[1])
    if v == false then return 'MISSING' end
    local parts = split(v, ':')  -- userId:currentJti
    if parts[2] ~= ARGV[1] then return 'CONFLICT' end
    local newVal = parts[1] .. ':' .. ARGV[2]
    redis.call('SET', KEYS[1], newVal, 'KEEPTTL')
    return parts[1]  -- return userId on success
    ```
    Return a sealed interface `CasResult` with variants `Success(userId: Long)`, `Missing`, `Conflict`. Use Lettuce's `eval()` with script caching (EVALSHA with fallback to EVAL).

  - `suspend fun deleteFamily(familyId: String)` — DEL key `token:family:{familyId}`. Used on logout to invalidate the entire refresh token family.

- **Lifecycle**: register Redis client shutdown in Ktor's `environment.monitor.subscribe(ApplicationStopped)` or use a similar hook to ensure clean disconnection.

## Files

- `kotlin/src/main/kotlin/lab/gabon/config/Redis.kt` — Lettuce RedisClient setup, coroutine commands extraction, shutdown hook
- `kotlin/src/main/kotlin/lab/gabon/service/TokenStore.kt` — RedisTokenStore with blacklist and family operations, CasResult sealed interface, Lua CAS script

## Verification

1. `./gradlew build` compiles without errors
2. With Redis running (`make up`), start the app — no Redis connection errors in logs
3. Test blacklist: call `setBlacklist("test-jti", 60s)`, verify Redis key `token:blacklist:test-jti` exists, `isBlacklisted("test-jti")` returns true, `isBlacklisted("other-jti")` returns false
4. Test family lifecycle: `setFamily("fam1", 42, "jti-a", 300s)` succeeds (Redis key `token:family:fam1`), `casFamily("fam1", "jti-a", "jti-b")` returns `Success(42)`, `casFamily("fam1", "jti-a", "jti-c")` returns `Conflict` (expected jti-a but current is jti-b), `deleteFamily("fam1")` then `casFamily("fam1", "jti-b", "jti-d")` returns `Missing`
5. After app shutdown, verify Redis connection is cleanly closed (no error logs, Lettuce shutdown messages visible)
