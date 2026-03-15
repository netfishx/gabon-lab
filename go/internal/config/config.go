package config

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

type Config struct {
	Port          int
	DatabaseURL   string
	RedisURL      string
	JWT           JWTConfig
	S3            S3Config
	Observability ObservabilityConfig
}

type ObservabilityConfig struct {
	LogLevel     string // debug/info/warn/error
	LogFormat    string // text/json
	OTLPEndpoint string // empty = disabled
	ServiceName  string
	DeployEnv    string
	EnablePprof  bool
	PprofPort    int
}

// S3Config holds S3-compatible storage settings.
// When Endpoint is empty, StorageService runs in stub mode.
type S3Config struct {
	Endpoint      string
	Region        string
	AccessKey     string
	SecretKey     string
	BucketVideos  string
	BucketAvatars string
}

type JWTConfig struct {
	CustomerSecret     string
	CustomerAccessTTL  time.Duration
	CustomerRefreshTTL time.Duration
	AdminSecret        string
	AdminAccessTTL     time.Duration
	AdminRefreshTTL    time.Duration
	CurrentKID         string
}

func Load() (*Config, error) {
	dbURL := os.Getenv("DATABASE_URL")
	if dbURL == "" {
		return nil, fmt.Errorf("DATABASE_URL is required")
	}

	redisURL := os.Getenv("REDIS_URL")
	if redisURL == "" {
		return nil, fmt.Errorf("REDIS_URL is required")
	}

	customerSecret := os.Getenv("JWT_CUSTOMER_SECRET")
	if customerSecret == "" {
		return nil, fmt.Errorf("JWT_CUSTOMER_SECRET is required")
	}

	adminSecret := os.Getenv("JWT_ADMIN_SECRET")
	if adminSecret == "" {
		return nil, fmt.Errorf("JWT_ADMIN_SECRET is required")
	}

	return &Config{
		Port:        getEnvInt("PORT", 8080),
		DatabaseURL: dbURL,
		RedisURL:    redisURL,
		S3: S3Config{
			Endpoint:      os.Getenv("S3_ENDPOINT"),
			Region:        getEnvString("S3_REGION", "garage"),
			AccessKey:     os.Getenv("S3_ACCESS_KEY"),
			SecretKey:     os.Getenv("S3_SECRET_KEY"),
			BucketVideos:  getEnvString("S3_BUCKET_VIDEOS", "gabon-videos"),
			BucketAvatars: getEnvString("S3_BUCKET_AVATARS", "gabon-avatars"),
		},
		JWT: JWTConfig{
			CustomerSecret:     customerSecret,
			CustomerAccessTTL:  getEnvDuration("JWT_CUSTOMER_ACCESS_TTL", 15*time.Minute),
			CustomerRefreshTTL: getEnvDuration("JWT_CUSTOMER_REFRESH_TTL", 7*24*time.Hour),
			AdminSecret:        adminSecret,
			AdminAccessTTL:     getEnvDuration("JWT_ADMIN_ACCESS_TTL", 15*time.Minute),
			AdminRefreshTTL:    getEnvDuration("JWT_ADMIN_REFRESH_TTL", 7*24*time.Hour),
			CurrentKID:         getEnvString("JWT_CURRENT_KID", "key-2026-02"),
		},
		Observability: ObservabilityConfig{
			LogLevel:     getEnvString("LOG_LEVEL", "info"),
			LogFormat:    getEnvString("LOG_FORMAT", "text"),
			OTLPEndpoint: os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT"),
			ServiceName:  getEnvString("OTEL_SERVICE_NAME", "gabon-api"),
			DeployEnv:    getEnvString("DEPLOY_ENV", "dev"),
			EnablePprof:  os.Getenv("ENABLE_PPROF") == "true",
			PprofPort:    getEnvInt("PPROF_PORT", 6060),
		},
	}, nil
}

func getEnvInt(key string, fallback int) int {
	v := os.Getenv(key)
	if v == "" {
		return fallback
	}
	i, err := strconv.Atoi(v)
	if err != nil {
		panic(fmt.Sprintf("config: %s=%q is not a valid integer: %v", key, v, err))
	}
	return i
}

func getEnvDuration(key string, fallback time.Duration) time.Duration {
	v := os.Getenv(key)
	if v == "" {
		return fallback
	}
	if d, err := time.ParseDuration(v); err == nil {
		return d
	}
	if secs, err := strconv.ParseInt(v, 10, 64); err == nil {
		return time.Duration(secs) * time.Second
	}
	panic(fmt.Sprintf("config: %s=%q is not a valid duration or seconds value", key, v))
}

func getEnvString(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
