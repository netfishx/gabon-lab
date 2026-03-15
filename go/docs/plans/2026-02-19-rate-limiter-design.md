# Rate Limiter Design

Redis-backed sliding window rate limiter for gabon-go.

## Context

- Frontend (Next.js SSR) calls Go API from server — all requests share same IP
- Native app (planned) calls Go API directly — real client IP
- Need `X-Forwarded-For` to extract real client IP for SSR scenario

## Algorithm

Sliding window counter via Redis sorted set. Per request:

```
now = current timestamp (microseconds)
window_start = now - 60s

Pipeline:
  ZREMRANGEBYSCORE key -inf window_start
  ZADD key now member(now)
  ZCARD key
  EXPIRE key 61
```

ZCARD > limit → reject with 429.

## Redis Key

```
rl:{group}:{identifier}
```

- `rl:auth:1.2.3.4` — auth endpoints, by IP
- `rl:pub:1.2.3.4` — public endpoints, by IP
- `rl:user:12345` — authenticated endpoints, by UserID
- `rl:admin:67` — admin endpoints, by UserID

## Route Groups and Thresholds

| Group | Routes | Dimension | Limit/min |
|-------|--------|-----------|-----------|
| auth | `/api/v1/auth/*` | IP | 20 |
| pub | `/api/v1/videos` (public), `/api/v1/users/:id` (public) | IP | 120 |
| user | `/api/v1/*` (authenticated) | UserID | 200 |
| admin | `/admin/v1/*` | UserID | 200 |

## Middleware Chain Position

```
Recovery → RequestID → Logger → CORS → BodyLimit → RateLimiter → ContextTimeout → Routes
```

RateLimiter after BodyLimit (reject oversized first), before Timeout (don't waste timeout on rejected requests).

## 429 Response

```json
{"code": "RATE_LIMITED", "message": "too many requests, please try again later", "data": null}
```

Headers: `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`.

## Files

| File | Change |
|------|--------|
| `internal/transport/middleware/ratelimit.go` | Sliding window core + middleware factory |
| `internal/transport/router.go` | Mount per-group rate limits |
| `internal/model/errors.go` | Add `ErrRateLimited` |
