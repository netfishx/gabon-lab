package middleware

import (
	"context"
	"fmt"
	"math/rand/v2"
	"net/http"
	"strconv"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/redis/go-redis/v9"
)

// RateLimitConfig defines a sliding window rate limiter.
type RateLimitConfig struct {
	Redis   *redis.Client
	Group   string                        // key prefix: auth / pub / user / admin
	Limit   int                           // max requests per window
	Window  time.Duration                 // sliding window size
	KeyFunc func(ctx huma.Context) string // extract identifier (IP or UserID)
}

// RateLimit returns a Huma middleware using a Redis sorted-set sliding window.
func RateLimit(cfg RateLimitConfig) func(huma.Context, func(huma.Context)) {
	return func(ctx huma.Context, next func(huma.Context)) {
		key := cfg.KeyFunc(ctx)
		if key == "" {
			next(ctx)
			return
		}

		redisKey := fmt.Sprintf("rl:%s:%s", cfg.Group, key)
		now := time.Now()
		nowMicro := float64(now.UnixMicro())
		windowStart := float64(now.Add(-cfg.Window).UnixMicro())

		count, err := slidingWindowCount(ctx.Context(), cfg.Redis, redisKey, windowStart, nowMicro, cfg.Window)
		if err != nil {
			writeError(ctx, http.StatusServiceUnavailable, "service temporarily unavailable")
			return
		}

		remaining := cfg.Limit - int(count)
		if remaining < 0 {
			remaining = 0
		}

		ctx.SetHeader("X-RateLimit-Limit", strconv.Itoa(cfg.Limit))
		ctx.SetHeader("X-RateLimit-Remaining", strconv.Itoa(remaining))

		if int(count) > cfg.Limit {
			retryAfter := int(cfg.Window.Seconds())
			ctx.SetHeader("Retry-After", strconv.Itoa(retryAfter))
			writeError(ctx, http.StatusTooManyRequests, "too many requests, please try again later")
			return
		}

		next(ctx)
	}
}

func slidingWindowCount(ctx context.Context, rdb *redis.Client, key string, windowStart, now float64, window time.Duration) (int64, error) {
	pipe := rdb.Pipeline()
	pipe.ZRemRangeByScore(ctx, key, "-inf", strconv.FormatFloat(windowStart, 'f', 0, 64))
	member := fmt.Sprintf("%.0f:%x", now, rand.Uint32())
	pipe.ZAdd(ctx, key, redis.Z{Score: now, Member: member})
	cardCmd := pipe.ZCard(ctx, key)
	pipe.Expire(ctx, key, window+time.Second)

	_, err := pipe.Exec(ctx)
	if err != nil {
		return 0, err
	}

	return cardCmd.Val(), nil
}

// KeyByRealIP extracts the real client IP from context (set by InjectRealIP).
func KeyByRealIP(ctx huma.Context) string {
	if ip, ok := ctx.Context().Value(RealIPKey).(string); ok && ip != "" {
		return ip
	}
	return resolveRealIP(ctx)
}

// KeyByCustomerID extracts the customer_id set by auth middleware.
func KeyByCustomerID(ctx huma.Context) string {
	id, ok := ctx.Context().Value(CustomerIDKey).(int64)
	if !ok {
		return KeyByRealIP(ctx)
	}
	return strconv.FormatInt(id, 10)
}

// KeyByAdminID extracts the admin_id set by auth middleware.
func KeyByAdminID(ctx huma.Context) string {
	id, ok := ctx.Context().Value(AdminIDKey).(int64)
	if !ok {
		return KeyByRealIP(ctx)
	}
	return strconv.FormatInt(id, 10)
}
