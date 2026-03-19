# Task 015: Rate Limiting implementation

**type**: impl
**depends-on**: [015-ratelimit-test]

## Description

Implement Redis ZSET sliding window rate limiter as a Ktor route-scoped plugin to pass the Feature 9 BDD tests.

Key implementation areas:

- **Sliding Window Algorithm** (Redis ZSET):
  1. Key format: `rate:{group}:{identifier}` (e.g., `rate:auth:10.0.0.1`, `rate:user:42`).
  2. On each request: `ZREMRANGEBYSCORE key 0 (now - window)` to remove expired entries.
  3. `ZCARD key` to count current window entries.
  4. If count >= limit, reject with 429.
  5. Otherwise, `ZADD key now now` to record the request.
  6. `EXPIRE key window` to auto-cleanup.
  7. Execute steps 2-6 as a Redis pipeline for atomicity.

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
