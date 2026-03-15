package transport

import (
	"net/http"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/danielgtaylor/huma/v2/adapters/humaecho"
	"github.com/labstack/echo/v4"
	echomw "github.com/labstack/echo/v4/middleware"
	"github.com/redis/go-redis/v9"
	"go.opentelemetry.io/contrib/instrumentation/github.com/labstack/echo/otelecho"

	"gabon-go/internal/transport/middleware"
)

// Handler aggregates all domain handlers.
type Handler struct {
	Auth   *AuthHandler
	User   *UserHandler
	Video  *VideoHandler
	Task   *TaskHandler
	Admin  *AdminHandler
	Report *ReportHandler
}

// RateLimitMultiplier scales all rate limits. Use >1 in integration tests to avoid 429s.
var RateLimitMultiplier = 1

// RegisterRoutes sets up Echo middleware, creates the Huma API, and registers all routes.
func RegisterRoutes(e *echo.Echo, h *Handler, authCfg middleware.AuthConfig, rdb *redis.Client) {
	e.HTTPErrorHandler = CustomErrorHandler

	// Echo-level middleware
	e.Use(middleware.Recovery())
	e.Use(middleware.RequestID())
	e.Use(otelecho.Middleware("gabon-api"))
	e.Use(middleware.Logger())
	e.Use(echomw.CORSWithConfig(echomw.CORSConfig{
		AllowOrigins: []string{"*"},
	}))
	e.Use(echomw.BodyLimit("50M"))
	e.Use(echomw.ContextTimeoutWithConfig(echomw.ContextTimeoutConfig{
		Timeout: 30 * time.Second,
		Skipper: func(c echo.Context) bool {
			p := c.Path()
			return p == "/api/v1/videos/upload" || p == "/api/v1/users/me/avatar"
		},
	}))

	// Health check (pure Echo, outside Huma)
	e.GET("/health", func(c echo.Context) error {
		return c.JSON(http.StatusOK, map[string]string{"status": "ok"})
	})

	// Huma API with OpenAPI 3.1 auto-generation
	config := huma.DefaultConfig("Gabon API", "1.0.0")
	config.Components.SecuritySchemes = map[string]*huma.SecurityScheme{
		"bearerAuth": {
			Type:         "http",
			Scheme:       "bearer",
			BearerFormat: "JWT",
		},
	}
	api := humaecho.New(e, config)

	// Global Huma middleware (runs before per-operation middleware)
	api.UseMiddleware(middleware.StoreHumaContext())
	api.UseMiddleware(middleware.InjectRealIP())

	// Rate limit configs
	m := RateLimitMultiplier
	rlAuth := middleware.RateLimitConfig{
		Redis: rdb, Group: "auth", Limit: 20 * m, Window: time.Minute,
		KeyFunc: middleware.KeyByRealIP,
	}
	rlPub := middleware.RateLimitConfig{
		Redis: rdb, Group: "pub", Limit: 120 * m, Window: time.Minute,
		KeyFunc: middleware.KeyByRealIP,
	}
	rlUser := middleware.RateLimitConfig{
		Redis: rdb, Group: "user", Limit: 200 * m, Window: time.Minute,
		KeyFunc: middleware.KeyByCustomerID,
	}
	rlAdmin := middleware.RateLimitConfig{
		Redis: rdb, Group: "admin", Limit: 200 * m, Window: time.Minute,
		KeyFunc: middleware.KeyByAdminID,
	}

	// Register handler routes
	h.Auth.RegisterRoutes(api, authCfg, rlAuth)
	h.User.RegisterRoutes(api, authCfg, rlPub, rlUser)
	h.Video.RegisterRoutes(api, authCfg, rlPub, rlUser)
	h.Task.RegisterRoutes(api, authCfg, rlUser)
	h.Admin.RegisterRoutes(api, authCfg, rlAuth, rlAdmin)
	h.Report.RegisterRoutes(api, authCfg, rlAdmin)
}
