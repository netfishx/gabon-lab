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
