package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	_ "net/http/pprof"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/labstack/echo/v4"
	"github.com/redis/go-redis/v9"

	"gabon-go/internal/config"
	"gabon-go/internal/observability"
	"gabon-go/internal/repository"
	"gabon-go/internal/service"
	"gabon-go/internal/transport"
	"gabon-go/internal/transport/middleware"
)

func main() {
	if err := run(); err != nil {
		slog.Error("fatal", "error", err)
		os.Exit(1)
	}
}

func run() error {
	cfg, err := config.Load()
	if err != nil {
		return fmt.Errorf("load config: %w", err)
	}

	// Observability
	logProvider, otelShutdown, err := observability.SetupOTel(
		context.Background(),
		cfg.Observability.OTLPEndpoint,
		cfg.Observability.ServiceName,
		cfg.Observability.DeployEnv,
	)
	if err != nil {
		return fmt.Errorf("setup otel: %w", err)
	}
	defer func() { _ = otelShutdown(context.Background()) }()

	observability.SetupLogging(cfg.Observability.LogLevel, cfg.Observability.LogFormat, logProvider)
	slog.Info("observability initialized",
		"otel_enabled", cfg.Observability.OTLPEndpoint != "",
		"log_level", cfg.Observability.LogLevel,
		"log_format", cfg.Observability.LogFormat,
	)

	// pprof (optional)
	if cfg.Observability.EnablePprof {
		go func() {
			pprofAddr := fmt.Sprintf(":%d", cfg.Observability.PprofPort)
			slog.Info("pprof server starting", "addr", pprofAddr)
			if err := http.ListenAndServe(pprofAddr, nil); err != nil {
				slog.Error("pprof server error", "error", err)
			}
		}()
	}

	// Database
	pool, err := pgxpool.New(context.Background(), cfg.DatabaseURL)
	if err != nil {
		return fmt.Errorf("connect database: %w", err)
	}
	defer pool.Close()

	if err := pool.Ping(context.Background()); err != nil {
		return fmt.Errorf("ping database: %w", err)
	}
	slog.Info("database connected")

	// Redis
	redisOpts, err := redis.ParseURL(cfg.RedisURL)
	if err != nil {
		return fmt.Errorf("parse redis URL: %w", err)
	}
	rdb := redis.NewClient(redisOpts)
	defer func() { _ = rdb.Close() }()

	if err := rdb.Ping(context.Background()).Err(); err != nil {
		return fmt.Errorf("ping redis: %w", err)
	}
	slog.Info("redis connected")

	// Dependencies
	queries := repository.New(pool)
	tokenStore := service.NewRedisTokenStore(rdb)
	jwtSvc := service.NewJWTService(&cfg.JWT)
	authSvc := service.NewAuthService(queries, tokenStore, jwtSvc, cfg.JWT.CustomerRefreshTTL)
	storageSvc := service.NewStorageService(&cfg.S3)
	userSvc := service.NewUserService(queries, storageSvc)
	videoSvc := service.NewVideoService(queries, storageSvc)
	taskSvc := service.NewTaskService(queries, pool, func(tx pgx.Tx) service.TaskRepo {
		return repository.New(tx)
	})
	adminSvc := service.NewAdminService(queries, tokenStore, jwtSvc, cfg.JWT.AdminRefreshTTL)
	reportSvc := service.NewReportService(queries)

	// Handlers
	handler := &transport.Handler{
		Auth:   transport.NewAuthHandler(authSvc),
		User:   transport.NewUserHandler(userSvc),
		Video:  transport.NewVideoHandler(videoSvc, taskSvc),
		Task:   transport.NewTaskHandler(taskSvc),
		Admin:  transport.NewAdminHandler(adminSvc),
		Report: transport.NewReportHandler(reportSvc),
	}

	authCfg := middleware.AuthConfig{
		JWT:        jwtSvc,
		TokenStore: tokenStore,
	}

	// Echo
	e := echo.New()
	e.HideBanner = true
	transport.RegisterRoutes(e, handler, authCfg, rdb)

	// Graceful shutdown
	go func() {
		addr := fmt.Sprintf(":%d", cfg.Port)
		slog.Info("server starting", "addr", addr)
		if err := e.Start(addr); err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("server error", "error", err)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, os.Interrupt, syscall.SIGTERM)
	<-quit

	slog.Info("shutting down server")
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	return e.Shutdown(ctx)
}
