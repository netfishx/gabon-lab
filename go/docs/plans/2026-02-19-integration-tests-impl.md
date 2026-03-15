# Integration Tests Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add 12 integration tests (4 CI-gate concurrency + 8 critical-path E2E) using testcontainers-go, httptest, real PG and Redis.

**Architecture:** Single `TestMain` spins up PG + Redis containers, runs goose migrations, constructs the full dependency chain (StorageService uses fake URL — uploads bypassed via direct DB inserts), and starts an httptest server. All tests share the containers and use unique username prefixes for isolation.

**Tech Stack:** testcontainers-go (postgres + redis modules), goose/v3 (programmatic migration), httptest, testify

---

## Task 1: Install testcontainers-go dependencies + scaffold

**Files:**
- Modify: `go.mod`
- Create: `tests/integration/setup.go` (same package as test files — no subdirectory)

**Step 1: Install dependencies**

Run:
```bash
go get github.com/testcontainers/testcontainers-go
go get github.com/testcontainers/testcontainers-go/modules/postgres
go get github.com/testcontainers/testcontainers-go/modules/redis
go get github.com/pressly/goose/v3
```

**Step 2: Write tests/integration/setup.go**

```go
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

// stubStorage is a no-op StorageService replacement.
type stubStorage struct{}

func (s *stubStorage) Upload(_ context.Context, bucket, path, _ string, _ io.Reader) (string, error) {
	return fmt.Sprintf("https://fake-storage/%s/%s", bucket, path), nil
}

func (s *stubStorage) Delete(_ context.Context, _, _ string) error {
	return nil
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
		log.Fatalf("pg connection string: %v", err)
	}

	// Redis container
	redisCtr, err := tcredis.Run(ctx, "redis:7-alpine")
	if err != nil {
		log.Fatalf("start redis: %v", err)
	}

	redisURL, err := redisCtr.ConnectionString(ctx)
	if err != nil {
		log.Fatalf("redis connection string: %v", err)
	}

	// Goose migrations
	sqlDB, err := sql.Open("pgx", pgURL)
	if err != nil {
		log.Fatalf("sql.Open: %v", err)
	}
	goose.SetDialect("postgres")
	if err := goose.Up(sqlDB, "../../db/migrations"); err != nil {
		log.Fatalf("goose up: %v", err)
	}
	sqlDB.Close()

	// pgxpool for app + test queries
	pool, err := pgxpool.New(ctx, pgURL)
	if err != nil {
		log.Fatalf("pgxpool: %v", err)
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

	storageSvc := service.NewStorageService(&config.SupabaseConfig{
		URL:        "https://fake-storage",
		ServiceKey: "fake-key",
	})
	// Note: storageSvc is real but Upload will fail since URL is fake.
	// Video upload tests use InsertVideo() to directly DB-insert instead.

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
	transport.RegisterRoutes(e, handler, authCfg, rdb)

	server := httptest.NewServer(e)

	cleanup := func() {
		server.Close()
		pool.Close()
		_ = rdb.Close()
		_ = testcontainers.TerminateContainer(pgCtr)
		_ = testcontainers.TerminateContainer(redisCtr)
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
func (e *TestEnv) InsertTaskProgress(t *testing.T, customerID, taskID int64, periodKey string, targetCount, rewardDiamonds int32, status int16) int64 {
	t.Helper()
	var id int64
	err := e.DB.QueryRow(context.Background(),
		`INSERT INTO task_progress (customer_id, task_id, current_count, target_count, period_key, task_status, reward_diamonds)
		 VALUES ($1, $2, $3, $4, $5, $6, $7)
		 RETURNING id`,
		customerID, taskID, targetCount, targetCount, periodKey, status, rewardDiamonds,
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

// Request sends an HTTP request to the test server.
func (e *TestEnv) Request(t *testing.T, method, path, body, token string) *http.Response {
	t.Helper()
	var bodyReader io.Reader
	if body != "" {
		bodyReader = strings.NewReader(body)
	}
	req, err := http.NewRequest(method, e.Server.URL+path, bodyReader)
	require.NoError(t, err)
	req.Header.Set("Content-Type", "application/json")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	resp, err := http.DefaultClient.Do(req)
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

func (e *TestEnv) parseTokens(t *testing.T, resp *http.Response) AuthTokens {
	t.Helper()
	body := e.ReadBody(t, resp)
	_, tokens := ParseResponse[AuthTokens](t, body)
	require.NotEmpty(t, tokens.AccessToken)
	require.NotEmpty(t, tokens.RefreshToken)
	return tokens
}
```

**Step 3: Verify compilation**

Run: `go build -tags=integration ./tests/integration/testutil/...`
Expected: No errors (may need to adjust imports based on actual service interfaces)

**Step 4: Commit**

```bash
git add go.mod go.sum tests/integration/testutil/setup.go
git commit -m "feat(test): add integration test infrastructure

- Add testcontainers-go for PG + Redis containers
- Add testutil.Setup() with goose migration
- Add helper methods for fixture creation and HTTP requests

Provides foundation for integration tests."
```

---

## Task 2: auth_test.go — CI gate + critical path

**Files:**
- Create: `tests/integration/auth_test.go`

**Step 1: Write auth_test.go**

```go
//go:build integration

package integration

import (
	"fmt"
	"net/http"
	"os"
	"sync"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

var env *TestEnv

func TestMain(m *testing.M) {
	var cleanup func()
	env, cleanup = Setup()
	code := m.Run()
	cleanup()
	os.Exit(code)
}

// --- CI Gate: Concurrent Refresh ---

func TestConcurrentRefresh(t *testing.T) {
	tokens := env.RegisterUser(t, "conc_refresh_user", "password123")

	const n = 10
	results := make(chan int, n)
	var wg sync.WaitGroup

	for i := 0; i < n; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			body := fmt.Sprintf(`{"refresh_token":"%s"}`, tokens.RefreshToken)
			resp := env.Request(t, http.MethodPost, "/api/v1/auth/refresh", body, "")
			defer resp.Body.Close()
			results <- resp.StatusCode
		}()
	}
	wg.Wait()
	close(results)

	successCount := 0
	for code := range results {
		if code == http.StatusOK {
			successCount++
		} else {
			assert.Equal(t, http.StatusUnauthorized, code)
		}
	}
	assert.Equal(t, 1, successCount, "exactly one refresh should succeed")
}

// --- CI Gate: Logout Then Refresh ---

func TestLogoutThenRefresh(t *testing.T) {
	tokens := env.RegisterUser(t, "logout_refresh_user", "password123")

	// Logout with access token
	resp := env.Request(t, http.MethodPost, "/api/v1/auth/logout", "", tokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// Try refresh — should fail
	body := fmt.Sprintf(`{"refresh_token":"%s"}`, tokens.RefreshToken)
	resp = env.Request(t, http.MethodPost, "/api/v1/auth/refresh", body, "")
	assert.Equal(t, http.StatusUnauthorized, resp.StatusCode)
	resp.Body.Close()
}

// --- Critical Path: Full Auth Lifecycle ---

func TestFullAuthLifecycle(t *testing.T) {
	username := "lifecycle_user"
	password := "password123"
	newPassword := "newpassword456"

	// Register
	tokens := env.RegisterUser(t, username, password)

	// Get me
	resp := env.Request(t, http.MethodGet, "/api/v1/auth/me", "", tokens.AccessToken)
	body := env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	code, _ := ParseResponse[map[string]any](t, body)
	assert.Equal(t, "OK", code)

	// Change password
	changePwdBody := fmt.Sprintf(`{"old_password":"%s","new_password":"%s"}`, password, newPassword)
	resp = env.Request(t, http.MethodPut, "/api/v1/auth/password", changePwdBody, tokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// Login with new password
	newTokens := env.LoginUser(t, username, newPassword)
	assert.NotEmpty(t, newTokens.AccessToken)

	// Old password should fail
	resp = env.Request(t, http.MethodPost, "/api/v1/auth/login",
		fmt.Sprintf(`{"username":"%s","password":"%s"}`, username, password), "")
	assert.Equal(t, http.StatusUnauthorized, resp.StatusCode)
	resp.Body.Close()
}

// --- Critical Path: Duplicate Register ---

func TestDuplicateRegister(t *testing.T) {
	username := "dup_register_user"
	env.RegisterUser(t, username, "password123")

	// Second registration should fail
	body := fmt.Sprintf(`{"username":"%s","password":"password123"}`, username)
	resp := env.Request(t, http.MethodPost, "/api/v1/auth/register", body, "")
	assert.Equal(t, http.StatusConflict, resp.StatusCode)

	respBody := env.ReadBody(t, resp)
	code, _ := ParseResponse[any](t, respBody)
	assert.Equal(t, "AUTH_USERNAME_EXISTS", code)
}
```

**Step 2: Run tests**

Run: `go test -v -race -count=1 -tags=integration -timeout 5m ./tests/integration/...`
Expected: All 4 tests PASS

**Step 3: Commit**

```bash
git add tests/integration/auth_test.go
git commit -m "test(integration): add auth tests

- Add TestConcurrentRefresh (CI gate)
- Add TestLogoutThenRefresh (CI gate)
- Add TestFullAuthLifecycle (register/login/password)
- Add TestDuplicateRegister (409 conflict)

Covers authentication concurrency safety and lifecycle."
```

---

## Task 3: video_test.go — CI gate + critical path

**Files:**
- Create: `tests/integration/video_test.go`

**Step 1: Write video_test.go**

```go
//go:build integration

package integration

import (
	"fmt"
	"net/http"
	"sync"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// --- CI Gate: Concurrent Like ---

func TestConcurrentLike(t *testing.T) {
	// Create video owner
	env.RegisterUser(t, "like_owner", "password123")
	ownerID := env.GetCustomerID(t, "like_owner")
	videoID := env.InsertVideo(t, ownerID, "concurrent like test")

	// Create 10 likers
	const n = 10
	likerTokens := make([]AuthTokens, n)
	for i := 0; i < n; i++ {
		likerTokens[i] = env.RegisterUser(t, fmt.Sprintf("liker_%02d", i), "password123")
	}

	// Concurrent likes
	var wg sync.WaitGroup
	for i := 0; i < n; i++ {
		wg.Add(1)
		go func(token string) {
			defer wg.Done()
			resp := env.Request(t, http.MethodPost, fmt.Sprintf("/api/v1/videos/%d/like", videoID), "", token)
			defer resp.Body.Close()
		}(likerTokens[i].AccessToken)
	}
	wg.Wait()

	// Verify like_count
	count := env.GetVideoLikeCount(t, videoID)
	assert.Equal(t, int64(n), count, "like_count should equal number of unique likers")
}

// --- Critical Path: Video List, Detail, Like/Unlike ---

func TestVideoListAndLike(t *testing.T) {
	tokens := env.RegisterUser(t, "video_list_user", "password123")
	customerID := env.GetCustomerID(t, "video_list_user")
	videoID := env.InsertVideo(t, customerID, "test video for list")

	// List videos
	resp := env.Request(t, http.MethodGet, "/api/v1/videos?page=1&page_size=20", "", "")
	body := env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	code, _ := ParseResponse[map[string]any](t, body)
	assert.Equal(t, "OK", code)

	// Get video detail
	resp = env.Request(t, http.MethodGet, fmt.Sprintf("/api/v1/videos/%d", videoID), "", tokens.AccessToken)
	body = env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	_, detail := ParseResponse[map[string]any](t, body)
	assert.Equal(t, false, detail["is_liked"])

	// Like
	resp = env.Request(t, http.MethodPost, fmt.Sprintf("/api/v1/videos/%d/like", videoID), "", tokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// Verify is_liked = true
	resp = env.Request(t, http.MethodGet, fmt.Sprintf("/api/v1/videos/%d", videoID), "", tokens.AccessToken)
	body = env.ReadBody(t, resp)
	_, detail = ParseResponse[map[string]any](t, body)
	assert.Equal(t, true, detail["is_liked"])

	// Unlike
	resp = env.Request(t, http.MethodDelete, fmt.Sprintf("/api/v1/videos/%d/like", videoID), "", tokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// Verify is_liked = false
	resp = env.Request(t, http.MethodGet, fmt.Sprintf("/api/v1/videos/%d", videoID), "", tokens.AccessToken)
	body = env.ReadBody(t, resp)
	_, detail = ParseResponse[map[string]any](t, body)
	assert.Equal(t, false, detail["is_liked"])
}

// --- Critical Path: Play Records ---

func TestPlayRecords(t *testing.T) {
	tokens := env.RegisterUser(t, "play_records_user", "password123")
	customerID := env.GetCustomerID(t, "play_records_user")
	videoID := env.InsertVideo(t, customerID, "play records test")

	// Play click
	resp := env.Request(t, http.MethodPost, fmt.Sprintf("/api/v1/videos/%d/play-click", videoID), "", tokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// Valid play
	resp = env.Request(t, http.MethodPost, fmt.Sprintf("/api/v1/videos/%d/play-valid", videoID), "", tokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// Verify counts via detail endpoint
	resp = env.Request(t, http.MethodGet, fmt.Sprintf("/api/v1/videos/%d", videoID), "", tokens.AccessToken)
	body := env.ReadBody(t, resp)
	_, detail := ParseResponse[map[string]any](t, body)

	// total_clicks and valid_clicks should be at least 1
	assert.GreaterOrEqual(t, detail["total_clicks"].(float64), float64(1))
	assert.GreaterOrEqual(t, detail["valid_clicks"].(float64), float64(1))
}
```

**Step 2: Run tests**

Run: `go test -v -race -count=1 -tags=integration -timeout 5m ./tests/integration/...`
Expected: All tests PASS (auth + video)

**Step 3: Commit**

```bash
git add tests/integration/video_test.go
git commit -m "test(integration): add video tests

- Add TestConcurrentLike (CI gate, 10 users)
- Add TestVideoListAndLike (list/detail/like/unlike)
- Add TestPlayRecords (click + valid play counts)

Covers video concurrency safety and CRUD."
```

---

## Task 4: task_test.go — CI gate + critical path

**Files:**
- Create: `tests/integration/task_test.go`

**Step 1: Write task_test.go**

```go
//go:build integration

package integration

import (
	"fmt"
	"net/http"
	"sync"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"gabon-go/internal/service"
)

// --- CI Gate: Concurrent Claim ---

func TestConcurrentClaim(t *testing.T) {
	tokens := env.RegisterUser(t, "conc_claim_user", "password123")
	customerID := env.GetCustomerID(t, "conc_claim_user")

	taskDefID := env.InsertTaskDefinition(t, "CONC_CLAIM_TEST", "Concurrent Claim Test", 1, 1, 1, 100)
	periodKey := service.PeriodKey(1, time.Now())
	progressID := env.InsertTaskProgress(t, customerID, taskDefID, periodKey, 1, 100, 2) // status=2 (completed)

	balanceBefore := env.GetDiamondBalance(t, customerID)

	const n = 10
	results := make(chan int, n)
	var wg sync.WaitGroup

	for i := 0; i < n; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			resp := env.Request(t, http.MethodPost,
				fmt.Sprintf("/api/v1/tasks/%d/claim", progressID), "", tokens.AccessToken)
			defer resp.Body.Close()
			results <- resp.StatusCode
		}()
	}
	wg.Wait()
	close(results)

	successCount := 0
	for code := range results {
		if code == http.StatusOK {
			successCount++
		}
	}
	assert.Equal(t, 1, successCount, "exactly one claim should succeed")

	balanceAfter := env.GetDiamondBalance(t, customerID)
	assert.Equal(t, balanceBefore+100, balanceAfter, "diamonds should increase by reward amount exactly once")
}

// --- Critical Path: Task Progress and Claim ---

func TestTaskProgressAndClaim(t *testing.T) {
	tokens := env.RegisterUser(t, "task_progress_user", "password123")
	customerID := env.GetCustomerID(t, "task_progress_user")

	// Insert a watch task (category=1) requiring 2 valid plays
	env.InsertTaskDefinition(t, "WATCH_2_PLAYS", "Watch 2 Videos", 1, 1, 2, 50)

	// Insert a video to play
	videoID := env.InsertVideo(t, customerID, "task progress video")

	// List tasks (creates progress via upsert)
	resp := env.Request(t, http.MethodGet, "/api/v1/tasks", "", tokens.AccessToken)
	body := env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	code, _ := ParseResponse[any](t, body)
	assert.Equal(t, "OK", code)

	// Two valid plays to complete the task
	for i := 0; i < 2; i++ {
		resp = env.Request(t, http.MethodPost, fmt.Sprintf("/api/v1/videos/%d/play-valid", videoID), "", tokens.AccessToken)
		require.Equal(t, http.StatusOK, resp.StatusCode)
		resp.Body.Close()
	}

	// Get progress ID from task list
	resp = env.Request(t, http.MethodGet, "/api/v1/tasks", "", tokens.AccessToken)
	body = env.ReadBody(t, resp)
	_, taskData := ParseResponse[[]map[string]any](t, body)

	var progressID int64
	var taskStatus float64
	for _, task := range taskData {
		if task["task_code"] == "WATCH_2_PLAYS" {
			progressID = int64(task["progress_id"].(float64))
			taskStatus = task["task_status"].(float64)
			break
		}
	}
	require.NotZero(t, progressID, "should find task progress")
	require.Equal(t, float64(2), taskStatus, "task should be completed after reaching target count")

	// Claim reward
	balanceBefore := env.GetDiamondBalance(t, customerID)
	resp = env.Request(t, http.MethodPost, fmt.Sprintf("/api/v1/tasks/%d/claim", progressID), "", tokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	balanceAfter := env.GetDiamondBalance(t, customerID)
	assert.Equal(t, balanceBefore+50, balanceAfter)

	// Claim again should fail
	resp = env.Request(t, http.MethodPost, fmt.Sprintf("/api/v1/tasks/%d/claim", progressID), "", tokens.AccessToken)
	assert.NotEqual(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()
}
```

**Step 2: Run tests**

Run: `go test -v -race -count=1 -tags=integration -timeout 5m ./tests/integration/...`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add tests/integration/task_test.go
git commit -m "test(integration): add task tests

- Add TestConcurrentClaim (CI gate, 10 goroutines)
- Add TestTaskProgressAndClaim (progress + claim lifecycle)

Covers task concurrency safety and reward flow."
```

---

## Task 5: user_test.go — critical path

**Files:**
- Create: `tests/integration/user_test.go`

**Step 1: Write user_test.go**

```go
//go:build integration

package integration

import (
	"fmt"
	"net/http"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// --- Critical Path: Follow/Unfollow ---

func TestFollowUnfollow(t *testing.T) {
	tokensA := env.RegisterUser(t, "follow_user_a", "password123")
	tokensB := env.RegisterUser(t, "follow_user_b", "password123")
	userBID := env.GetCustomerID(t, "follow_user_b")

	// A follows B
	resp := env.Request(t, http.MethodPost, fmt.Sprintf("/api/v1/users/%d/follow", userBID), "", tokensA.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// A's following list should contain B
	resp = env.Request(t, http.MethodGet, "/api/v1/users/me/following?page=1&page_size=20", "", tokensA.AccessToken)
	body := env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	_, followingData := ParseResponse[map[string]any](t, body)
	items := followingData["items"].([]any)
	assert.GreaterOrEqual(t, len(items), 1)

	// B's followers list should contain A
	resp = env.Request(t, http.MethodGet, fmt.Sprintf("/api/v1/users/%d/followers?page=1&page_size=20", userBID), "", tokensB.AccessToken)
	body = env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	_, followersData := ParseResponse[map[string]any](t, body)
	fItems := followersData["items"].([]any)
	assert.GreaterOrEqual(t, len(fItems), 1)

	// A unfollows B
	resp = env.Request(t, http.MethodDelete, fmt.Sprintf("/api/v1/users/%d/follow", userBID), "", tokensA.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// A's following list should be empty (for this pair)
	resp = env.Request(t, http.MethodGet, "/api/v1/users/me/following?page=1&page_size=20", "", tokensA.AccessToken)
	body = env.ReadBody(t, resp)
	_, followingAfter := ParseResponse[map[string]any](t, body)
	assert.Equal(t, float64(0), followingAfter["total"])
}

// --- Critical Path: Follow Self ---

func TestFollowSelf(t *testing.T) {
	tokens := env.RegisterUser(t, "follow_self_user", "password123")
	userID := env.GetCustomerID(t, "follow_self_user")

	resp := env.Request(t, http.MethodPost, fmt.Sprintf("/api/v1/users/%d/follow", userID), "", tokens.AccessToken)
	assert.Equal(t, http.StatusBadRequest, resp.StatusCode)

	body := env.ReadBody(t, resp)
	code, _ := ParseResponse[any](t, body)
	assert.Equal(t, "USER_CANNOT_FOLLOW_SELF", code)
}

// --- Critical Path: Update Profile ---

func TestUpdateProfile(t *testing.T) {
	tokens := env.RegisterUser(t, "profile_update_user", "password123")

	// Update profile
	updateBody := `{"name":"Test Name","phone":"1234567890","email":"test@example.com","avatar_url":"","signature":"Hello world"}`
	resp := env.Request(t, http.MethodPut, "/api/v1/users/me/profile", updateBody, tokens.AccessToken)
	body := env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)

	code, profile := ParseResponse[map[string]any](t, body)
	assert.Equal(t, "OK", code)
	assert.Equal(t, "Test Name", profile["name"])
	assert.Equal(t, "1234567890", profile["phone"])
	assert.Equal(t, "test@example.com", profile["email"])
	assert.Equal(t, "Hello world", profile["signature"])

	// Verify via GET
	resp = env.Request(t, http.MethodGet, "/api/v1/users/me/profile", "", tokens.AccessToken)
	body = env.ReadBody(t, resp)
	_, fetched := ParseResponse[map[string]any](t, body)
	assert.Equal(t, "Test Name", fetched["name"])
	assert.Equal(t, "Hello world", fetched["signature"])
}
```

**Step 2: Run tests**

Run: `go test -v -race -count=1 -tags=integration -timeout 5m ./tests/integration/...`
Expected: All 12 tests PASS

**Step 3: Commit**

```bash
git add tests/integration/user_test.go
git commit -m "test(integration): add user tests

- Add TestFollowUnfollow (follow/list/unfollow)
- Add TestFollowSelf (400 self-follow)
- Add TestUpdateProfile (update and verify fields)

Covers user social features and profile management."
```

---

## Task 6: Update CI workflow + Makefile

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `Makefile`

**Step 1: Update Makefile test-integration target**

Ensure the Makefile `test-integration` target matches:
```makefile
test-integration:
	go test -v -race -count=1 -tags=integration -timeout 5m ./tests/integration/...
```

**Step 2: Add integration-test job to ci.yml**

Add after the existing `test` job:

```yaml
  integration-test:
    runs-on: ubuntu-latest
    needs: [lint, test]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-go@v5
        with:
          go-version: "1.26"
      - run: make test-integration
```

**Step 3: Run full test suite locally**

Run: `make test && make test-integration`
Expected: Both pass

**Step 4: Commit**

```bash
git add Makefile .github/workflows/ci.yml
git commit -m "ci: add integration test job

- Update Makefile test-integration target
- Add integration-test job after lint + unit tests

Ensures concurrency gate tests run on every PR."
```
