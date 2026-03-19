package lab.gabon.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lab.gabon.common.ApiResponse;
import lab.gabon.common.AppError;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis sliding-window rate limiter implemented as a servlet filter.
 *
 * <p>Uses a sorted set per key: ZREMRANGEBYSCORE + ZADD + ZCARD + EXPIRE in a single Lua script.
 * Mirrors the Go/Kotlin implementations for consistent behaviour across all backends.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private final StringRedisTemplate redis;
  private final RedisScript<Long> rateLimitScript;
  private final ObjectMapper mapper;
  private final String group;
  private final int limit;
  private final Duration window;
  private final boolean useUserId;

  public RateLimitFilter(
      StringRedisTemplate redis,
      RedisScript<Long> rateLimitScript,
      ObjectMapper mapper,
      String group,
      int limit,
      Duration window,
      boolean useUserId) {
    this.redis = redis;
    this.rateLimitScript = rateLimitScript;
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
    var member = nowMicro + ":" + Integer.toHexString(ThreadLocalRandom.current().nextInt());

    var count = slidingWindowCount(redisKey, windowStartMicro, nowMicro, member);

    var remaining = Math.max(limit - (int) count, 0);
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

  private long slidingWindowCount(String key, double windowStart, double now, String member) {
    Long count =
        redis.execute(
            rateLimitScript,
            List.of(key),
            String.valueOf(windowStart),
            String.valueOf(now),
            member,
            String.valueOf(window.toSeconds() + 1));
    return count != null ? count : 0;
  }
}
