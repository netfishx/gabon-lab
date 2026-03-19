package lab.gabon.service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lab.gabon.config.AppConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * Redis-backed token store for JWT blacklisting and refresh-token family tracking.
 *
 * <p>Key layout:
 *
 * <ul>
 *   <li>{@code token:blacklist:{jti}} -- value "1", TTL = access token remaining lifetime
 *   <li>{@code token:family:{familyId}} -- value "{userId}:{currentJti}", TTL = refresh token
 *       lifetime
 * </ul>
 */
@Service
public class TokenStore {

  /** Result of a compare-and-swap on a refresh token family. */
  public sealed interface CasResult {
    /** CAS succeeded; the family now points to the new JTI. */
    record Success(long userId) implements CasResult {}

    /** The family key does not exist (expired or deleted). */
    record Missing() implements CasResult {}

    /** The current JTI did not match (replay detected). Family was deleted. */
    record Conflict() implements CasResult {}
  }

  private static final long CAS_MISSING = -1L;
  private static final long CAS_CONFLICT = -2L;

  private final StringRedisTemplate redis;
  private final RedisScript<Long> casScript;
  private final AppConfig.JwtConfig jwtConfig;

  public TokenStore(
      StringRedisTemplate redis,
      @Qualifier("refreshCasScript") RedisScript<Long> casScript,
      AppConfig appConfig) {
    this.redis = redis;
    this.casScript = casScript;
    this.jwtConfig = appConfig.jwt();
  }

  // -- Refresh Token Family ---------------------------------------------------

  /** Create or overwrite a token family entry with the appropriate refresh TTL. */
  public void storeRefreshFamily(String familyId, long userId, String refreshJti, boolean admin) {
    var ttl = admin ? jwtConfig.adminRefreshTtl() : jwtConfig.customerRefreshTtl();
    var key = "token:family:" + familyId;
    var value = userId + ":" + refreshJti;
    redis.opsForValue().set(key, value, ttl.toSeconds(), TimeUnit.SECONDS);
  }

  /**
   * Atomically compare-and-swap the current JTI in a token family.
   *
   * @return userId on success, or Missing/Conflict result
   */
  public CasResult rotateRefreshToken(String familyId, String oldJti, String newJti) {
    var key = "token:family:" + familyId;
    var result = redis.execute(casScript, List.of(key), oldJti, newJti);
    if (result == null || result == CAS_MISSING) {
      return new CasResult.Missing();
    }
    if (result == CAS_CONFLICT) {
      return new CasResult.Conflict();
    }
    return new CasResult.Success(result);
  }

  /** Delete an entire token family (e.g. on logout). */
  public void revokeFamily(String familyId) {
    redis.delete("token:family:" + familyId);
  }

  // -- Access Token Blacklist -------------------------------------------------

  /** Add a JTI to the blacklist with the appropriate access TTL. */
  public void blacklistAccessToken(String jti, boolean admin) {
    Duration ttl = admin ? jwtConfig.adminAccessTtl() : jwtConfig.customerAccessTtl();
    var key = "token:blacklist:" + jti;
    redis.opsForValue().set(key, "1", ttl.toSeconds(), TimeUnit.SECONDS);
  }

  /** Check whether a JTI has been blacklisted. */
  public boolean isBlacklisted(String jti) {
    return Boolean.TRUE.equals(redis.hasKey("token:blacklist:" + jti));
  }
}
