package lab.gabon.service

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands

/** Result of a compare-and-swap on a refresh token family. */
sealed interface CasResult {
    /** CAS succeeded; the family now points to the new JTI. */
    data class Success(
        val userId: Long,
    ) : CasResult

    /** The family key does not exist (expired or deleted). */
    data object Missing : CasResult

    /** The current JTI did not match the expected value (replay detected). Family was deleted. */
    data object Conflict : CasResult
}

/**
 * Redis-backed token store for JWT blacklisting and refresh-token family tracking.
 *
 * Key layout:
 * - `token:blacklist:{jti}` — value `"1"`, TTL = access token remaining lifetime
 * - `token:family:{familyId}` — value `"{userId}:{currentJti}"`, TTL = refresh token lifetime
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisTokenStore(
    private val redis: RedisCoroutinesCommands<String, String>,
) {
    // ── Blacklist ──────────────────────────────────────────────

    /** Add [jti] to the blacklist with the given TTL in seconds. */
    suspend fun setBlacklist(
        jti: String,
        ttlSeconds: Long,
    ) {
        redis.setex("token:blacklist:$jti", ttlSeconds, "1")
    }

    /** Check whether [jti] has been blacklisted. */
    suspend fun isBlacklisted(jti: String): Boolean = (redis.exists("token:blacklist:$jti") ?: 0L) > 0L

    // ── Refresh Token Family ───────────────────────────────────

    /** Create or overwrite a token family entry. */
    suspend fun setFamily(
        familyId: String,
        userId: Long,
        currentJti: String,
        ttlSeconds: Long,
    ) {
        redis.setex("token:family:$familyId", ttlSeconds, "$userId:$currentJti")
    }

    /**
     * Atomically compare-and-swap the current JTI in a token family.
     *
     * Lua script behaviour:
     * - Key missing → return `-1` → [CasResult.Missing]
     * - Current JTI doesn't match [expectedJti] → delete key, return `-2` → [CasResult.Conflict]
     * - Match → update value with [newJti] (KEEPTTL), return `userId` → [CasResult.Success]
     */
    suspend fun casFamily(
        familyId: String,
        expectedJti: String,
        newJti: String,
    ): CasResult {
        val result =
            redis.eval<Long>(
                CAS_SCRIPT,
                ScriptOutputType.INTEGER,
                arrayOf("token:family:$familyId"),
                expectedJti,
                newJti,
            )
        return when {
            result == null || result == CAS_MISSING -> CasResult.Missing
            result == CAS_CONFLICT -> CasResult.Conflict
            else -> CasResult.Success(userId = result)
        }
    }

    /** Delete an entire token family (e.g. on logout). */
    suspend fun deleteFamily(familyId: String) {
        redis.del("token:family:$familyId")
    }

    private companion object {
        const val CAS_MISSING = -1L
        const val CAS_CONFLICT = -2L

        /**
         * Lua CAS script for refresh token rotation.
         *
         * KEYS[1] = token:family:{familyId}
         * ARGV[1] = expectedJti
         * ARGV[2] = newJti
         *
         * Returns:
         *   userId (positive Long) on success
         *   -1 if key is missing
         *   -2 if JTI mismatch (replay attack — key is deleted)
         */
        val CAS_SCRIPT =
            """
            local v = redis.call('GET', KEYS[1])
            if v == false then return -1 end
            local sep = string.find(v, ':', 1, true)
            local uid = string.sub(v, 1, sep - 1)
            local jti = string.sub(v, sep + 1)
            if jti ~= ARGV[1] then
              redis.call('DEL', KEYS[1])
              return -2
            end
            redis.call('SET', KEYS[1], uid .. ':' .. ARGV[2], 'KEEPTTL')
            return tonumber(uid)
            """.trimIndent()
    }
}
