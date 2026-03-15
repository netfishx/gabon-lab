package middleware

import (
	"encoding/json"
	"net/http"
	"strings"

	"github.com/danielgtaylor/huma/v2"

	"gabon-go/internal/model"
	"gabon-go/internal/service"
)

// contextKey is the type for context value keys set by middleware.
type contextKey int

const (
	CustomerIDKey contextKey = iota
	AdminIDKey
	AdminRoleKey
	ClaimsKey
	RealIPKey
	HumaCtxKey // stores huma.Context for multipart access in handlers
)

// AuthConfig holds dependencies for auth middleware.
type AuthConfig struct {
	JWT        *service.JWTService
	TokenStore service.TokenStore
}

// RequireCustomerAuth requires a valid customer access token.
func RequireCustomerAuth(cfg AuthConfig) func(huma.Context, func(huma.Context)) {
	return customerAuth(cfg, true)
}

// OptionalCustomerAuth parses customer token if present, passes through if not.
func OptionalCustomerAuth(cfg AuthConfig) func(huma.Context, func(huma.Context)) {
	return customerAuth(cfg, false)
}

func customerAuth(cfg AuthConfig, required bool) func(huma.Context, func(huma.Context)) {
	return func(ctx huma.Context, next func(huma.Context)) {
		tokenStr := extractBearerToken(ctx)
		if tokenStr == "" {
			if required {
				writeError(ctx, http.StatusUnauthorized,
					string(model.ErrUnauthorized), "missing authorization token")
				return
			}
			next(ctx)
			return
		}

		claims, err := cfg.JWT.ParseCustomerToken(tokenStr)
		if err != nil {
			if required {
				writeError(ctx, http.StatusUnauthorized,
					string(model.ErrTokenInvalid), "invalid token")
				return
			}
			next(ctx)
			return
		}

		if claims.TokenType != "access" {
			if required {
				writeError(ctx, http.StatusUnauthorized,
					string(model.ErrTokenInvalid), "not an access token")
				return
			}
			next(ctx)
			return
		}

		blacklisted, err := cfg.TokenStore.IsBlacklisted(ctx.Context(), claims.JTI)
		if err != nil {
			writeError(ctx, http.StatusServiceUnavailable,
				"SERVICE_UNAVAILABLE", "unable to verify token status")
			return
		}
		if blacklisted {
			writeError(ctx, http.StatusUnauthorized,
				string(model.ErrTokenInvalid), "token has been revoked")
			return
		}

		ctx = huma.WithValue(ctx, CustomerIDKey, claims.UserID)
		ctx = huma.WithValue(ctx, ClaimsKey, claims)
		next(ctx)
	}
}

// RequireAdminAuth requires a valid admin access token.
func RequireAdminAuth(cfg AuthConfig) func(huma.Context, func(huma.Context)) {
	return func(ctx huma.Context, next func(huma.Context)) {
		tokenStr := extractBearerToken(ctx)
		if tokenStr == "" {
			writeError(ctx, http.StatusUnauthorized,
				string(model.ErrUnauthorized), "missing authorization token")
			return
		}

		claims, err := cfg.JWT.ParseAdminToken(tokenStr)
		if err != nil {
			writeError(ctx, http.StatusUnauthorized,
				string(model.ErrTokenInvalid), "invalid admin token")
			return
		}

		if claims.TokenType != "access" {
			writeError(ctx, http.StatusUnauthorized,
				string(model.ErrTokenInvalid), "not an access token")
			return
		}

		blacklisted, err := cfg.TokenStore.IsBlacklisted(ctx.Context(), claims.JTI)
		if err != nil {
			writeError(ctx, http.StatusServiceUnavailable,
				"SERVICE_UNAVAILABLE", "unable to verify token status")
			return
		}
		if blacklisted {
			writeError(ctx, http.StatusUnauthorized,
				string(model.ErrTokenInvalid), "token has been revoked")
			return
		}

		ctx = huma.WithValue(ctx, AdminIDKey, claims.UserID)
		ctx = huma.WithValue(ctx, AdminRoleKey, claims.Role)
		ctx = huma.WithValue(ctx, ClaimsKey, claims)
		next(ctx)
	}
}

// StoreHumaContext makes huma.Context available in handler's context.Context
// (needed for multipart form access in upload handlers).
func StoreHumaContext() func(huma.Context, func(huma.Context)) {
	return func(ctx huma.Context, next func(huma.Context)) {
		ctx = huma.WithValue(ctx, HumaCtxKey, ctx)
		next(ctx)
	}
}

// InjectRealIP resolves the client IP and stores it in context.
func InjectRealIP() func(huma.Context, func(huma.Context)) {
	return func(ctx huma.Context, next func(huma.Context)) {
		ctx = huma.WithValue(ctx, RealIPKey, resolveRealIP(ctx))
		next(ctx)
	}
}

func extractBearerToken(ctx huma.Context) string {
	auth := ctx.Header("Authorization")
	if len(auth) > 7 && strings.EqualFold(auth[:7], "BEARER ") {
		return auth[7:]
	}
	return ""
}

func resolveRealIP(ctx huma.Context) string {
	if xff := ctx.Header("X-Forwarded-For"); xff != "" {
		if i := strings.IndexByte(xff, ','); i > 0 {
			return strings.TrimSpace(xff[:i])
		}
		return strings.TrimSpace(xff)
	}
	if xri := ctx.Header("X-Real-Ip"); xri != "" {
		return xri
	}
	addr := ctx.RemoteAddr()
	if i := strings.LastIndexByte(addr, ':'); i > 0 {
		return addr[:i]
	}
	return addr
}

func writeError(ctx huma.Context, status int, code, message string) {
	ctx.SetStatus(status)
	ctx.SetHeader("Content-Type", "application/json")
	_ = json.NewEncoder(ctx.BodyWriter()).Encode(map[string]any{
		"code":    code,
		"message": message,
		"data":    nil,
	})
}
