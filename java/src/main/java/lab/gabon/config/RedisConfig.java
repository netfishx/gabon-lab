package lab.gabon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisConfig {

  @Bean
  public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
    return new StringRedisTemplate(factory);
  }

  /**
   * Lua script for atomic sliding-window rate limiting.
   *
   * <p>KEYS[1] = rate limit key<br>
   * ARGV[1] = windowStart (min score to remove)<br>
   * ARGV[2] = now (score for new member)<br>
   * ARGV[3] = member (unique value)<br>
   * ARGV[4] = TTL in seconds
   *
   * <p>Returns: current count after cleanup + insert
   */
  @Bean("rateLimitScript")
  public RedisScript<Long> rateLimitScript() {
    String lua =
        """
        redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
        redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3])
        local count = redis.call('ZCARD', KEYS[1])
        redis.call('EXPIRE', KEYS[1], ARGV[4])
        return count
        """;
    return RedisScript.of(lua, Long.class);
  }

  /**
   * Lua CAS script for atomic refresh token rotation.
   *
   * <p>KEYS[1] = token:family:{familyId}<br>
   * ARGV[1] = expectedJti<br>
   * ARGV[2] = newJti
   *
   * <p>Returns:<br>
   * userId (positive Long) on success<br>
   * -1 if key is missing<br>
   * -2 if JTI mismatch (replay attack -- key is deleted)
   */
  @Bean("refreshCasScript")
  public RedisScript<Long> refreshCasScript() {
    var script = new DefaultRedisScript<Long>();
    script.setScriptText(
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
        """);
    script.setResultType(Long.class);
    return script;
  }
}
