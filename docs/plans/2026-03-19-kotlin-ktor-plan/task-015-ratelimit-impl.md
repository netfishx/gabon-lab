# Task 015: Rate Limiting implementation

**type**: impl
**depends-on**: [015-ratelimit-test]

## Description

Implement Redis ZSET sliding window rate limiter as a Ktor route-scoped plugin to pass the Feature 9 BDD tests.

Key implementation areas:

- **Sliding Window Algorithm** (Redis ZSET + Lua script for atomicity):
  1. Key format: `rl:{group}:{identifier}` (e.g., `rl:auth:10.0.0.1`, `rl:user:42`).
  2. **MUST use a Lua script** (not pipeline) to guarantee atomicity under concurrent requests. A pipeline only batches commands — it does NOT prevent interleaving between concurrent clients. Two requests could both pass ZCARD before either executes ZADD, causing over-admission.
  3. Lua script logic (single atomic EVAL):
     ```
     KEYS[1] = rate limit key
     ARGV[1] = window start timestamp (now - window)
     ARGV[2] = current timestamp (unique per request, e.g., now_micros + random suffix)
     ARGV[3] = window TTL in seconds
     ARGV[4] = limit

     redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
     redis.call('ZADD', KEYS[1], ARGV[2], ARGV[2])
     local count = redis.call('ZCARD', KEYS[1])
     redis.call('EXPIRE', KEYS[1], ARGV[3])
     return count
     ```
  4. If returned count > limit, reject with 429 (the ZADD already happened, but that's fine — it will expire within the window and the count is correct for subsequent requests).
  5. This matches the Go implementation's approach (Redis atomic operation for concurrency safety).

- **Rate Limit Groups** (4 configurations):
  | Group | Limit | Window | Key | Applies To |
  |-------|-------|--------|-----|------------|
  | auth | 20/min | 60s | IP | register, login, refresh |
  | pub | 120/min | 60s | IP | public video/user listing |
  | user | 200/min | 60s | customer_id | authenticated user actions |
  | admin | 200/min | 60s | admin_id | all admin endpoints |

- **Response Headers**:
  - `X-RateLimit-Limit`: max requests for the group.
  - `X-RateLimit-Remaining`: remaining requests in current window.
  - `Retry-After`: seconds until window resets (only on 429).

- **429 Response Body**: `{ "code": 429, "message": "too many requests, please try again later", "data": null }`.

- **Plugin Architecture**: Implement as a Ktor `createRouteScopedPlugin` that can be applied per route group. The plugin extracts the identifier (IP from `request.origin.remoteAddress` or user ID from JWT claims) and checks against the configured limit.

- **Route Integration**: Apply the rate limit plugin to each route group in `plugin/Routing.kt`:
  - Auth routes: `rateLimit("auth", 20, KeyType.IP)`
  - Public routes: `rateLimit("pub", 120, KeyType.IP)`
  - User routes: `rateLimit("user", 200, KeyType.CUSTOMER_ID)`
  - Admin routes: `rateLimit("admin", 200, KeyType.ADMIN_ID)`

## Files

- `kotlin/src/main/kotlin/lab/gabon/plugin/RateLimit.kt` -- sliding window rate limiter plugin (ZSET algorithm, header injection, 429 handling)

## Verification

1. `./gradlew test --tests "lab.gabon.plugin.RateLimitTest"` -- all tests pass (Green)
2. Requests within limit return 200 with correct `X-RateLimit-Limit` and `X-RateLimit-Remaining` headers
3. Requests exceeding limit return 429 with `Retry-After` header
4. User rate limit keys by `customer_id` (different users have independent limits)
5. Public rate limit keys by IP (different IPs have independent limits)
6. After the window slides (61 seconds), previously blocked requests succeed again
