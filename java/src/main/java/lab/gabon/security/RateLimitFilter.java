package lab.gabon.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import lab.gabon.common.ApiResponse;
import lab.gabon.common.AppError;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Redis sliding-window rate limiter implemented as a servlet filter.
 *
 * <p>Uses a sorted set per key: ZREMRANGEBYSCORE + ZADD + ZCARD + EXPIRE in a single pipeline.
 * Mirrors the Go/Kotlin implementations for consistent behaviour across all backends.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;
  private final String group;
  private final int limit;
  private final Duration window;
  private final boolean useUserId;

  public RateLimitFilter(
      StringRedisTemplate redis,
      ObjectMapper mapper,
      String group,
      int limit,
      Duration window,
      boolean useUserId) {
    this.redis = redis;
    this.mapper = mapper;
    this.group = group;
    this.limit = limit;
    this.window = window;
    this.useUserId = useUserId;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var identifier = resolveKey(request);
    if (identifier == null || identifier.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    var redisKey = "rl:" + group + ":" + identifier;
    var nowMicro = System.currentTimeMillis() * 1000L + (System.nanoTime() % 1000L);
    var windowStartMicro = nowMicro - window.toSeconds() * 1_000_000L;

    var count = slidingWindowCount(redisKey, windowStartMicro, nowMicro);

    var remaining = Math.max(limit - count.intValue(), 0);
    response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
    response.setHeader("X-RateLimit-Remaining", Integer.toString(remaining));

    if (count > limit) {
      response.setHeader("Retry-After", Long.toString(window.toSeconds()));
      response.setStatus(429);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      mapper.writeValue(response.getOutputStream(), ApiResponse.error(new AppError.RateLimited()));
      return;
    }

    filterChain.doFilter(request, response);
  }

  private String resolveKey(HttpServletRequest request) {
    if (useUserId) {
      var userId = request.getAttribute("userId");
      if (userId != null) {
        return userId.toString();
      }
    }
    return request.getRemoteAddr();
  }

  private Long slidingWindowCount(String key, long windowStart, long now) {
    var results =
        redis.executePipelined(
            (RedisCallback<Object>)
                connection -> {
                  var conn = (StringRedisConnection) connection;
                  conn.zRemRangeByScore(key, Double.NEGATIVE_INFINITY, windowStart);
                  var member =
                      now + ":" + Integer.toHexString(ThreadLocalRandom.current().nextInt());
                  conn.zAdd(key, now, member);
                  conn.zCard(key);
                  conn.expire(key, window.toSeconds() + 1);
                  return null;
                });
    // Pipeline results: [zRemRangeByScore, zAdd, zCard, expire]
    // zCard is at index 2
    return (Long) results.get(2);
  }
}
