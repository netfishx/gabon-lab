//go:build integration

package integration

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/labstack/echo/v4"
	"github.com/pressly/goose/v3"
	goredis "github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/require"
	"github.com/testcontainers/testcontainers-go"
	tcpostgres "github.com/testcontainers/testcontainers-go/modules/postgres"
	tcredis "github.com/testcontainers/testcontainers-go/modules/redis"
	"golang.org/x/crypto/bcrypt"

	"gabon-go/internal/config"
	"gabon-go/internal/repository"
	"gabon-go/internal/service"
	"gabon-go/internal/transport"
	"gabon-go/internal/transport/middleware"

	_ "github.com/jackc/pgx/v5/stdlib" // database/sql driver for goose
)

// TestEnv holds all resources for integration tests.
type TestEnv struct {
	Server *httptest.Server
	DB     *pgxpool.Pool
	Redis  *goredis.Client
	JWTSvc *service.JWTService
}

// AuthTokens holds token pair from register/login.
type AuthTokens struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
}

// fatal terminates any already-started containers before exiting.
func fatal(ctx context.Context, msg string, ctrs ...testcontainers.Container) {
	for _, c := range ctrs {
		if c != nil {
			_ = testcontainers.TerminateContainer(c)
		}
	}
	log.Fatal(msg)
}

// Setup starts PG + Redis containers, runs migrations, and returns a TestEnv.
func Setup() (*TestEnv, func()) {
	ctx := context.Background()

	// PG container
	pgCtr, err := tcpostgres.Run(ctx,
		"postgres:16-alpine",
		tcpostgres.WithDatabase("testdb"),
		tcpostgres.WithUsername("testuser"),
		tcpostgres.WithPassword("testpass"),
		tcpostgres.BasicWaitStrategies(),
	)
	if err != nil {
		log.Fatalf("start postgres: %v", err)
	}

	pgURL, err := pgCtr.ConnectionString(ctx, "sslmode=disable")
	if err != nil {
		fatal(ctx, fmt.Sprintf("pg connection string: %v", err), pgCtr)
	}

	// Redis container
	redisCtr, err := tcredis.Run(ctx, "redis:7-alpine")
	if err != nil {
		fatal(ctx, fmt.Sprintf("start redis: %v", err), pgCtr)
	}

	redisURL, err := redisCtr.ConnectionString(ctx)
	if err != nil {
		fatal(ctx, fmt.Sprintf("redis connection string: %v", err), pgCtr, redisCtr)
	}

	// Goose migrations
	sqlDB, err := sql.Open("pgx", pgURL)
	if err != nil {
		fatal(ctx, fmt.Sprintf("sql.Open: %v", err), pgCtr, redisCtr)
	}
	if err := goose.SetDialect("postgres"); err != nil {
		_ = sqlDB.Close()
		fatal(ctx, fmt.Sprintf("goose dialect: %v", err), pgCtr, redisCtr)
	}
	if err := goose.Up(sqlDB, "../../db/migrations"); err != nil {
		_ = sqlDB.Close()
		fatal(ctx, fmt.Sprintf("goose up: %v", err), pgCtr, redisCtr)
	}
	_ = sqlDB.Close()

	// pgxpool for app + test queries
	pool, err := pgxpool.New(ctx, pgURL)
	if err != nil {
		fatal(ctx, fmt.Sprintf("pgxpool: %v", err), pgCtr, redisCtr)
	}

	// Redis client
	redisOpts, _ := goredis.ParseURL(redisURL)
	rdb := goredis.NewClient(redisOpts)

	// Dependencies (mirrors main.go)
	queries := repository.New(pool)
	tokenStore := service.NewRedisTokenStore(rdb)

	jwtCfg := config.JWTConfig{
		CustomerSecret:     "integration-test-customer-secret-32!",
		CustomerAccessTTL:  60 * time.Second,
		CustomerRefreshTTL: 120 * time.Second,
		AdminSecret:        "integration-test-admin-secret-32!!!",
		AdminAccessTTL:     60 * time.Second,
		AdminRefreshTTL:    120 * time.Second,
		CurrentKID:         "test-key-1",
	}
	jwtSvc := service.NewJWTService(&jwtCfg)

	authSvc := service.NewAuthService(queries, tokenStore, jwtSvc, jwtCfg.CustomerRefreshTTL)

	// StorageService in stub mode (no endpoint) — tests bypass upload via InsertVideo().
	storageSvc := service.NewStorageService(&config.S3Config{})

	userSvc := service.NewUserService(queries, storageSvc)
	videoSvc := service.NewVideoService(queries, storageSvc)
	taskSvc := service.NewTaskService(queries, pool, func(tx pgx.Tx) service.TaskRepo {
		return repository.New(tx)
	})
	adminSvc := service.NewAdminService(queries, tokenStore, jwtSvc, jwtCfg.AdminRefreshTTL)
	reportSvc := service.NewReportService(queries)

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

	e := echo.New()
	e.HideBanner = true
	transport.RateLimitMultiplier = 100 // relax rate limits for integration tests
	transport.RegisterRoutes(e, handler, authCfg, rdb)

	server := httptest.NewServer(e)

	cleanup := func() {
		server.Close()
		pool.Close()
		_ = rdb.Close()
		_ = testcontainers.TerminateContainer(redisCtr)
		_ = testcontainers.TerminateContainer(pgCtr)
	}

	return &TestEnv{
		Server: server,
		DB:     pool,
		Redis:  rdb,
		JWTSvc: jwtSvc,
	}, cleanup
}

// --- Helper methods ---

// RegisterUser registers a user and returns tokens.
func (e *TestEnv) RegisterUser(t *testing.T, username, password string) AuthTokens {
	t.Helper()
	body := fmt.Sprintf(`{"username":"%s","password":"%s"}`, username, password)
	resp := e.Request(t, http.MethodPost, "/api/v1/auth/register", body, "")
	require.Equal(t, http.StatusCreated, resp.StatusCode)
	return e.parseTokens(t, resp)
}

// LoginUser logs in and returns tokens.
func (e *TestEnv) LoginUser(t *testing.T, username, password string) AuthTokens {
	t.Helper()
	body := fmt.Sprintf(`{"username":"%s","password":"%s"}`, username, password)
	resp := e.Request(t, http.MethodPost, "/api/v1/auth/login", body, "")
	require.Equal(t, http.StatusOK, resp.StatusCode)
	return e.parseTokens(t, resp)
}

// InsertVideo inserts an approved video directly into DB, returns video ID.
func (e *TestEnv) InsertVideo(t *testing.T, customerID int64, title string) int64 {
	t.Helper()
	var id int64
	err := e.DB.QueryRow(context.Background(),
		`INSERT INTO videos (customer_id, title, file_name, file_size, file_url, mime_type, status)
		 VALUES ($1, $2, 'test.mp4', 1024, 'https://fake/test.mp4', 'video/mp4', 4)
		 RETURNING id`,
		customerID, title,
	).Scan(&id)
	require.NoError(t, err)
	return id
}

// InsertTaskDefinition inserts a task definition, returns its ID.
func (e *TestEnv) InsertTaskDefinition(t *testing.T, taskCode, taskName string, taskType, taskCategory int16, targetCount, rewardDiamonds int32) int64 {
	t.Helper()
	var id int64
	err := e.DB.QueryRow(context.Background(),
		`INSERT INTO task_definitions (task_code, task_name, task_type, task_category, target_count, reward_diamonds, status)
		 VALUES ($1, $2, $3, $4, $5, $6, 1)
		 RETURNING id`,
		taskCode, taskName, taskType, taskCategory, targetCount, rewardDiamonds,
	).Scan(&id)
	require.NoError(t, err)
	return id
}

// InsertTaskProgress inserts a task progress row, returns progress ID.
// When status=3 (claimed), claimed_at is set to now automatically.
func (e *TestEnv) InsertTaskProgress(t *testing.T, customerID, taskID int64, periodKey string, targetCount, rewardDiamonds int32, status int16) int64 {
	t.Helper()
	var claimedAt any
	if status == 3 {
		claimedAt = time.Now()
	}
	var id int64
	err := e.DB.QueryRow(context.Background(),
		`INSERT INTO task_progress (customer_id, task_id, current_count, target_count, period_key, task_status, reward_diamonds, claimed_at)
		 VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
		 RETURNING id`,
		customerID, taskID, targetCount, targetCount, periodKey, status, rewardDiamonds, claimedAt,
	).Scan(&id)
	require.NoError(t, err)
	return id
}

// GetDiamondBalance returns a customer's diamond balance from DB.
func (e *TestEnv) GetDiamondBalance(t *testing.T, customerID int64) int64 {
	t.Helper()
	var balance int64
	err := e.DB.QueryRow(context.Background(),
		`SELECT diamond_balance FROM customers WHERE id = $1`, customerID,
	).Scan(&balance)
	require.NoError(t, err)
	return balance
}

// GetVideoLikeCount returns a video's like_count from DB.
func (e *TestEnv) GetVideoLikeCount(t *testing.T, videoID int64) int64 {
	t.Helper()
	var count int64
	err := e.DB.QueryRow(context.Background(),
		`SELECT like_count FROM videos WHERE id = $1`, videoID,
	).Scan(&count)
	require.NoError(t, err)
	return count
}

// GetCustomerID extracts customer ID from DB by username.
func (e *TestEnv) GetCustomerID(t *testing.T, username string) int64 {
	t.Helper()
	var id int64
	err := e.DB.QueryRow(context.Background(),
		`SELECT id FROM customers WHERE LOWER(username) = LOWER($1) AND deleted_at IS NULL`, username,
	).Scan(&id)
	require.NoError(t, err)
	return id
}

// RequestRaw sends an HTTP request without test assertions (safe for goroutines).
func (e *TestEnv) RequestRaw(method, path, body, token string) (*http.Response, error) {
	var bodyReader io.Reader
	if body != "" {
		bodyReader = strings.NewReader(body)
	}
	req, err := http.NewRequest(method, e.Server.URL+path, bodyReader)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	return http.DefaultClient.Do(req)
}

// Request sends an HTTP request to the test server.
func (e *TestEnv) Request(t *testing.T, method, path, body, token string) *http.Response {
	t.Helper()
	resp, err := e.RequestRaw(method, path, body, token)
	require.NoError(t, err)
	return resp
}

// ReadBody reads and closes a response body.
func (e *TestEnv) ReadBody(t *testing.T, resp *http.Response) []byte {
	t.Helper()
	defer resp.Body.Close()
	b, err := io.ReadAll(resp.Body)
	require.NoError(t, err)
	return b
}

// ParseResponse parses a standard {code, message, data} response.
func ParseResponse[T any](t *testing.T, body []byte) (string, T) {
	t.Helper()
	var resp struct {
		Code    string `json:"code"`
		Message string `json:"message"`
		Data    T      `json:"data"`
	}
	require.NoError(t, json.Unmarshal(body, &resp))
	return resp.Code, resp.Data
}

// InsertAdminUser inserts an admin user with bcrypt-hashed password, returns admin ID.
func (e *TestEnv) InsertAdminUser(t *testing.T, username, password string, role int16) int64 {
	t.Helper()
	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	require.NoError(t, err)
	var id int64
	err = e.DB.QueryRow(context.Background(),
		`INSERT INTO admin_users (username, password_hash, role, full_name, status)
		 VALUES ($1, $2, $3, $4, 1)
		 RETURNING id`,
		username, string(hash), role, username,
	).Scan(&id)
	require.NoError(t, err)
	return id
}

// AdminLogin logs in as admin and returns tokens.
func (e *TestEnv) AdminLogin(t *testing.T, username, password string) AuthTokens {
	t.Helper()
	body := fmt.Sprintf(`{"username":"%s","password":"%s"}`, username, password)
	resp := e.Request(t, http.MethodPost, "/admin/v1/auth/login", body, "")
	require.Equal(t, http.StatusOK, resp.StatusCode)
	return e.parseTokens(t, resp)
}

// GetVideoStatus returns a video's status from DB.
func (e *TestEnv) GetVideoStatus(t *testing.T, videoID int64) int16 {
	t.Helper()
	var status int16
	err := e.DB.QueryRow(context.Background(),
		`SELECT status FROM videos WHERE id = $1`, videoID,
	).Scan(&status)
	require.NoError(t, err)
	return status
}

// InsertPendingVideo inserts a video with status=1 (pending review), returns video ID.
func (e *TestEnv) InsertPendingVideo(t *testing.T, customerID int64, title string) int64 {
	t.Helper()
	var id int64
	err := e.DB.QueryRow(context.Background(),
		`INSERT INTO videos (customer_id, title, file_name, file_size, file_url, mime_type, status)
		 VALUES ($1, $2, 'test.mp4', 1024, 'https://fake/test.mp4', 'video/mp4', 1)
		 RETURNING id`,
		customerID, title,
	).Scan(&id)
	require.NoError(t, err)
	return id
}

// GetAdminID extracts admin user ID from DB by username.
func (e *TestEnv) GetAdminID(t *testing.T, username string) int64 {
	t.Helper()
	var id int64
	err := e.DB.QueryRow(context.Background(),
		`SELECT id FROM admin_users WHERE LOWER(username) = LOWER($1) AND deleted_at IS NULL`, username,
	).Scan(&id)
	require.NoError(t, err)
	return id
}

func (e *TestEnv) parseTokens(t *testing.T, resp *http.Response) AuthTokens {
	t.Helper()
	body := e.ReadBody(t, resp)
	_, tokens := ParseResponse[AuthTokens](t, body)
	require.NotEmpty(t, tokens.AccessToken)
	require.NotEmpty(t, tokens.RefreshToken)
	return tokens
}
