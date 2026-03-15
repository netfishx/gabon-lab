# Gabon Go Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a production-grade Go backend using Echo + sqlc + pgx to replace the gabon Java backend.

**Architecture:** Three-layer (transport → service → repository), single binary, route-prefix role separation. See `docs/plans/2026-02-19-gabon-go-design.md` for full design.

**Tech Stack:** Go 1.26, Echo v4, pgx/v5, sqlc, goose, golang-jwt/v5, go-redis/v9, slog, testify, golangci-lint

---

## Task 1: Install Go toolchain

**Files:**
- Create: `gabon-go/.golangci.yml`
- Create: `gabon-go/Makefile`

**Step 1: Install sqlc, goose, golangci-lint**

Run:
```bash
go install github.com/sqlc-dev/sqlc/cmd/sqlc@latest
go install github.com/pressly/goose/v3/cmd/goose@latest
brew install golangci-lint
```

**Step 2: Verify installations**

Run: `sqlc version && goose --version && golangci-lint --version`
Expected: all three print version numbers

**Step 3: Create .golangci.yml**

```yaml
run:
  timeout: 5m

linters:
  enable:
    - errcheck
    - govet
    - staticcheck
    - unused
    - gosimple
    - ineffassign
    - typecheck
    - gofmt
    - goimports
    - misspell
    - unconvert
    - gocritic

linters-settings:
  gocritic:
    enabled-tags:
      - diagnostic
      - style
      - performance

issues:
  exclude-use-default: false
  max-issues-per-linter: 0
  max-same-issues: 0
```

**Step 4: Create Makefile**

```makefile
.PHONY: dev build lint test test-integration migrate sqlc

# Dev
dev:
	go run cmd/api/main.go

build:
	go build -o bin/api cmd/api/main.go

# Quality
lint:
	golangci-lint run ./...

test:
	go test -v -race -count=1 ./...

test-integration:
	go test -v -race -count=1 -tags=integration ./...

# Database
migrate:
	goose -dir db/migrations postgres "$$DATABASE_URL" up

migrate-down:
	goose -dir db/migrations postgres "$$DATABASE_URL" down

migrate-status:
	goose -dir db/migrations postgres "$$DATABASE_URL" status

# Code generation
sqlc:
	sqlc generate
```

**Step 5: Commit**

```bash
git add .golangci.yml Makefile
git commit -m "chore: add golangci-lint config and Makefile"
```

---

## Task 2: Project skeleton — go.mod, config, .env

**Files:**
- Create: `go.mod` (via go mod init)
- Create: `.env.example`
- Create: `.gitignore`
- Create: `internal/config/config.go`
- Test: `internal/config/config_test.go`

**Step 1: Initialize Go module**

Run: `go mod init github.com/user/gabon-go`

**Step 2: Create .gitignore**

```gitignore
bin/
.env
*.exe
*.test
*.out
vendor/
```

**Step 3: Create .env.example**

```env
# Server
PORT=8080

# Database (Supabase direct connect, NOT pooler)
DATABASE_URL=postgres://postgres:[password]@db.[project].supabase.co:5432/postgres

# Redis
REDIS_URL=redis://localhost:6379/0

# JWT - Customer
JWT_CUSTOMER_SECRET=change-me-customer-secret-min-32-chars
JWT_CUSTOMER_ACCESS_TTL=15m
JWT_CUSTOMER_REFRESH_TTL=168h

# JWT - Admin
JWT_ADMIN_SECRET=change-me-admin-secret-min-32-chars
JWT_ADMIN_ACCESS_TTL=15m
JWT_ADMIN_REFRESH_TTL=168h

# JWT - Key ID for rotation
JWT_CURRENT_KID=key-2026-02
```

**Step 4: Write the failing config test**

```go
// internal/config/config_test.go
package config

import (
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestLoad_Defaults(t *testing.T) {
	os.Setenv("DATABASE_URL", "postgres://localhost/test")
	os.Setenv("REDIS_URL", "redis://localhost:6379/0")
	os.Setenv("JWT_CUSTOMER_SECRET", "test-secret-32-chars-minimum!!!!!")
	os.Setenv("JWT_ADMIN_SECRET", "test-admin-secret-32-chars-min!!!!!")
	defer func() {
		os.Unsetenv("DATABASE_URL")
		os.Unsetenv("REDIS_URL")
		os.Unsetenv("JWT_CUSTOMER_SECRET")
		os.Unsetenv("JWT_ADMIN_SECRET")
	}()

	cfg, err := Load()
	require.NoError(t, err)
	assert.Equal(t, 8080, cfg.Port)
	assert.Equal(t, "postgres://localhost/test", cfg.DatabaseURL)
}

func TestLoad_MissingRequired(t *testing.T) {
	os.Clearenv()
	_, err := Load()
	assert.Error(t, err)
}
```

**Step 5: Run test to verify it fails**

Run: `go test ./internal/config/ -v`
Expected: FAIL — package/function not found

**Step 6: Write config implementation**

```go
// internal/config/config.go
package config

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

type Config struct {
	Port        int
	DatabaseURL string
	RedisURL    string
	JWT         JWTConfig
}

type JWTConfig struct {
	CustomerSecret    string
	CustomerAccessTTL time.Duration
	CustomerRefreshTTL time.Duration
	AdminSecret       string
	AdminAccessTTL    time.Duration
	AdminRefreshTTL   time.Duration
	CurrentKID        string
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
		JWT: JWTConfig{
			CustomerSecret:     customerSecret,
			CustomerAccessTTL:  getEnvDuration("JWT_CUSTOMER_ACCESS_TTL", 15*time.Minute),
			CustomerRefreshTTL: getEnvDuration("JWT_CUSTOMER_REFRESH_TTL", 7*24*time.Hour),
			AdminSecret:        adminSecret,
			AdminAccessTTL:     getEnvDuration("JWT_ADMIN_ACCESS_TTL", 15*time.Minute),
			AdminRefreshTTL:    getEnvDuration("JWT_ADMIN_REFRESH_TTL", 7*24*time.Hour),
			CurrentKID:         getEnvString("JWT_CURRENT_KID", "key-2026-02"),
		},
	}, nil
}

func getEnvInt(key string, fallback int) int {
	if v := os.Getenv(key); v != "" {
		if i, err := strconv.Atoi(v); err == nil {
			return i
		}
	}
	return fallback
}

func getEnvDuration(key string, fallback time.Duration) time.Duration {
	if v := os.Getenv(key); v != "" {
		if d, err := time.ParseDuration(v); err == nil {
			return d
		}
	}
	return fallback
}

func getEnvString(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
```

**Step 7: Install dependency and run test**

Run: `go get github.com/stretchr/testify && go test ./internal/config/ -v`
Expected: PASS

**Step 8: Commit**

```bash
git add go.mod go.sum .gitignore .env.example internal/config/
git commit -m "feat(config): add config loader with env vars"
```

---

## Task 3: Error model + unified response

**Files:**
- Create: `internal/model/errors.go`
- Create: `internal/transport/response.go`
- Test: `internal/model/errors_test.go`
- Test: `internal/transport/response_test.go`

**Step 1: Write failing errors test**

```go
// internal/model/errors_test.go
package model

import (
	"errors"
	"net/http"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestAppError_Error(t *testing.T) {
	err := NewAppError(ErrUnauthorized, "not logged in")
	assert.Equal(t, "UNAUTHORIZED: not logged in", err.Error())
	assert.Equal(t, http.StatusUnauthorized, err.Status)
}

func TestAppError_Unwrap(t *testing.T) {
	inner := errors.New("db connection failed")
	err := WrapError(ErrInternal, "something broke", inner)
	assert.True(t, errors.Is(err, inner))
}

func TestAppError_Is(t *testing.T) {
	err := NewAppError(ErrNotFound, "video not found")
	var appErr *AppError
	assert.True(t, errors.As(err, &appErr))
	assert.Equal(t, ErrNotFound, appErr.Code)
}
```

**Step 2: Run test to verify it fails**

Run: `go test ./internal/model/ -v`
Expected: FAIL

**Step 3: Write errors implementation**

```go
// internal/model/errors.go
package model

import "net/http"

type ErrorCode string

const (
	ErrOK                  ErrorCode = "OK"
	ErrInternal            ErrorCode = "INTERNAL_ERROR"
	ErrBadRequest          ErrorCode = "BAD_REQUEST"
	ErrNotFound            ErrorCode = "NOT_FOUND"
	ErrUnauthorized        ErrorCode = "UNAUTHORIZED"
	ErrForbidden           ErrorCode = "FORBIDDEN"
	ErrInvalidCredentials  ErrorCode = "AUTH_INVALID_CREDENTIALS"
	ErrTokenExpired        ErrorCode = "AUTH_TOKEN_EXPIRED"
	ErrTokenInvalid        ErrorCode = "AUTH_TOKEN_INVALID"
	ErrUsernameExists      ErrorCode = "AUTH_USERNAME_EXISTS"
	ErrPasswordMismatch    ErrorCode = "AUTH_PASSWORD_MISMATCH"
	ErrVideoNotFound       ErrorCode = "VIDEO_NOT_FOUND"
	ErrVideoNotApproved    ErrorCode = "VIDEO_NOT_APPROVED"
	ErrAlreadyLiked        ErrorCode = "VIDEO_ALREADY_LIKED"
	ErrAlreadyFollowing    ErrorCode = "USER_ALREADY_FOLLOWING"
	ErrCannotFollowSelf    ErrorCode = "USER_CANNOT_FOLLOW_SELF"
	ErrTaskNotClaimable    ErrorCode = "TASK_NOT_CLAIMABLE"
)

var statusMap = map[ErrorCode]int{
	ErrInternal:           http.StatusInternalServerError,
	ErrBadRequest:         http.StatusBadRequest,
	ErrNotFound:           http.StatusNotFound,
	ErrUnauthorized:       http.StatusUnauthorized,
	ErrForbidden:          http.StatusForbidden,
	ErrInvalidCredentials: http.StatusUnauthorized,
	ErrTokenExpired:       http.StatusUnauthorized,
	ErrTokenInvalid:       http.StatusUnauthorized,
	ErrUsernameExists:     http.StatusConflict,
	ErrPasswordMismatch:   http.StatusBadRequest,
	ErrVideoNotFound:      http.StatusNotFound,
	ErrVideoNotApproved:   http.StatusForbidden,
	ErrAlreadyLiked:       http.StatusConflict,
	ErrAlreadyFollowing:   http.StatusConflict,
	ErrCannotFollowSelf:   http.StatusBadRequest,
	ErrTaskNotClaimable:   http.StatusBadRequest,
}

type AppError struct {
	Code    ErrorCode
	Message string
	Status  int
	Err     error
}

func (e *AppError) Error() string {
	return string(e.Code) + ": " + e.Message
}

func (e *AppError) Unwrap() error {
	return e.Err
}

func NewAppError(code ErrorCode, message string) *AppError {
	return &AppError{
		Code:    code,
		Message: message,
		Status:  statusMap[code],
	}
}

func WrapError(code ErrorCode, message string, err error) *AppError {
	return &AppError{
		Code:    code,
		Message: message,
		Status:  statusMap[code],
		Err:     err,
	}
}
```

**Step 4: Run test**

Run: `go test ./internal/model/ -v`
Expected: PASS

**Step 5: Write failing response test**

```go
// internal/transport/response_test.go
package transport

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/labstack/echo/v4"
	"github.com/stretchr/testify/assert"
	"github.com/user/gabon-go/internal/model"
)

func TestOK(t *testing.T) {
	e := echo.New()
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	rec := httptest.NewRecorder()
	c := e.NewContext(req, rec)

	err := OK(c, map[string]string{"name": "test"})
	assert.NoError(t, err)
	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), `"code":"OK"`)
	assert.Contains(t, rec.Body.String(), `"message":"ok"`)
}

func TestFail_AppError(t *testing.T) {
	e := echo.New()
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	rec := httptest.NewRecorder()
	c := e.NewContext(req, rec)

	appErr := model.NewAppError(model.ErrNotFound, "video not found")
	err := Fail(c, appErr)
	assert.NoError(t, err)
	assert.Equal(t, http.StatusNotFound, rec.Code)
	assert.Contains(t, rec.Body.String(), `"code":"NOT_FOUND"`)
}
```

**Step 6: Write response implementation**

```go
// internal/transport/response.go
package transport

import (
	"errors"
	"net/http"

	"github.com/labstack/echo/v4"
	"github.com/user/gabon-go/internal/model"
)

type Response struct {
	Code    string      `json:"code"`
	Message string      `json:"message"`
	Data    interface{} `json:"data"`
}

type PagedData struct {
	Items    interface{} `json:"items"`
	Total    int64       `json:"total"`
	Page     int         `json:"page"`
	PageSize int         `json:"page_size"`
}

func OK(c echo.Context, data interface{}) error {
	return c.JSON(http.StatusOK, Response{
		Code:    string(model.ErrOK),
		Message: "ok",
		Data:    data,
	})
}

func OKPaged(c echo.Context, items interface{}, total int64, page, pageSize int) error {
	return c.JSON(http.StatusOK, Response{
		Code:    string(model.ErrOK),
		Message: "ok",
		Data: PagedData{
			Items:    items,
			Total:    total,
			Page:     page,
			PageSize: pageSize,
		},
	})
}

func Fail(c echo.Context, err error) error {
	var appErr *model.AppError
	if errors.As(err, &appErr) {
		return c.JSON(appErr.Status, Response{
			Code:    string(appErr.Code),
			Message: appErr.Message,
			Data:    nil,
		})
	}
	return c.JSON(http.StatusInternalServerError, Response{
		Code:    string(model.ErrInternal),
		Message: "internal server error",
		Data:    nil,
	})
}

func CustomErrorHandler(err error, c echo.Context) {
	if c.Response().Committed {
		return
	}
	var he *echo.HTTPError
	if errors.As(err, &he) {
		msg, _ := he.Message.(string)
		_ = c.JSON(he.Code, Response{
			Code:    string(model.ErrInternal),
			Message: msg,
			Data:    nil,
		})
		return
	}
	_ = Fail(c, err)
}
```

**Step 7: Install Echo and run tests**

Run: `go get github.com/labstack/echo/v4 && go test ./internal/model/ ./internal/transport/ -v`
Expected: PASS

**Step 8: Commit**

```bash
git add internal/model/ internal/transport/
git commit -m "feat(core): add AppError model and unified response"
```

---

## Task 4: Middleware — Recovery, RequestID, Logger

**Files:**
- Create: `internal/transport/middleware/recovery.go`
- Create: `internal/transport/middleware/request_id.go`
- Create: `internal/transport/middleware/logger.go`
- Test: `internal/transport/middleware/middleware_test.go`

**Step 1: Write failing middleware test**

```go
// internal/transport/middleware/middleware_test.go
package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/labstack/echo/v4"
	"github.com/stretchr/testify/assert"
)

func TestRequestID_SetsHeader(t *testing.T) {
	e := echo.New()
	e.Use(RequestID())
	e.GET("/", func(c echo.Context) error {
		return c.String(http.StatusOK, "ok")
	})

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	assert.NotEmpty(t, rec.Header().Get("X-Request-ID"))
}

func TestRecovery_HandlesPanic(t *testing.T) {
	e := echo.New()
	e.Use(Recovery())
	e.GET("/", func(c echo.Context) error {
		panic("test panic")
	})

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusInternalServerError, rec.Code)
	assert.Contains(t, rec.Body.String(), "INTERNAL_ERROR")
}
```

**Step 2: Run test to verify it fails**

Run: `go test ./internal/transport/middleware/ -v`
Expected: FAIL

**Step 3: Write RequestID middleware**

```go
// internal/transport/middleware/request_id.go
package middleware

import (
	"github.com/google/uuid"
	"github.com/labstack/echo/v4"
)

const RequestIDHeader = "X-Request-ID"

func RequestID() echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			id := c.Request().Header.Get(RequestIDHeader)
			if id == "" {
				id = uuid.New().String()
			}
			c.Set("request_id", id)
			c.Response().Header().Set(RequestIDHeader, id)
			return next(c)
		}
	}
}
```

**Step 4: Write Recovery middleware**

```go
// internal/transport/middleware/recovery.go
package middleware

import (
	"fmt"
	"log/slog"
	"net/http"
	"runtime"

	"github.com/labstack/echo/v4"
	"github.com/user/gabon-go/internal/model"
)

func Recovery() echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) (returnErr error) {
			defer func() {
				if r := recover(); r != nil {
					buf := make([]byte, 4096)
					n := runtime.Stack(buf, false)
					slog.Error("panic recovered",
						"error", fmt.Sprintf("%v", r),
						"stack", string(buf[:n]),
						"request_id", c.Get("request_id"),
					)
					returnErr = c.JSON(http.StatusInternalServerError, map[string]interface{}{
						"code":    string(model.ErrInternal),
						"message": "internal server error",
						"data":    nil,
					})
				}
			}()
			return next(c)
		}
	}
}
```

**Step 5: Write Logger middleware**

```go
// internal/transport/middleware/logger.go
package middleware

import (
	"log/slog"
	"time"

	"github.com/labstack/echo/v4"
)

func Logger() echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			start := time.Now()
			err := next(c)
			slog.Info("request",
				"method", c.Request().Method,
				"path", c.Request().URL.Path,
				"status", c.Response().Status,
				"latency_ms", time.Since(start).Milliseconds(),
				"request_id", c.Get("request_id"),
			)
			return err
		}
	}
}
```

**Step 6: Install uuid and run tests**

Run: `go get github.com/google/uuid && go test ./internal/transport/middleware/ -v`
Expected: PASS

**Step 7: Run lint**

Run: `make lint`
Expected: no issues

**Step 8: Commit**

```bash
git add internal/transport/middleware/
git commit -m "feat(middleware): add Recovery, RequestID, Logger"
```

---

## Task 5: Database migration — all 8 tables

**Files:**
- Create: `db/migrations/001_init.sql`

**Step 1: Create migration file**

```sql
-- +goose Up

-- admin_users
CREATE TABLE admin_users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            SMALLINT NOT NULL DEFAULT 2,
    full_name       VARCHAR(255),
    phone           VARCHAR(50),
    avatar_url      VARCHAR(500),
    status          SMALLINT NOT NULL DEFAULT 1,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);
CREATE UNIQUE INDEX idx_admin_users_username_active
    ON admin_users(LOWER(username)) WHERE deleted_at IS NULL;

-- customers
CREATE TABLE customers (
    id                          BIGSERIAL PRIMARY KEY,
    username                    VARCHAR(100) NOT NULL,
    password_hash               VARCHAR(255) NOT NULL,
    name                        VARCHAR(255),
    phone                       VARCHAR(50),
    email                       VARCHAR(255),
    avatar_url                  VARCHAR(500),
    signature                   VARCHAR(255),
    is_vip                      BOOLEAN NOT NULL DEFAULT FALSE,
    diamond_balance             BIGINT NOT NULL DEFAULT 0,
    withdrawal_password_hash    VARCHAR(255),
    last_login_at               TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMPTZ
);
CREATE UNIQUE INDEX idx_customers_username_active
    ON customers(LOWER(username)) WHERE deleted_at IS NULL;

-- videos
CREATE TABLE videos (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    title           VARCHAR(500),
    description     TEXT,
    file_name       VARCHAR(255) NOT NULL,
    file_size       BIGINT NOT NULL,
    file_url        VARCHAR(500) NOT NULL,
    thumbnail_url   VARCHAR(500),
    preview_gif_url VARCHAR(500),
    mime_type       VARCHAR(100) NOT NULL,
    duration        INT,
    width           INT,
    height          INT,
    status          SMALLINT NOT NULL DEFAULT 1,
    review_notes    TEXT,
    reviewed_by     BIGINT REFERENCES admin_users(id),
    reviewed_at     TIMESTAMPTZ,
    total_clicks    BIGINT NOT NULL DEFAULT 0,
    valid_clicks    BIGINT NOT NULL DEFAULT 0,
    like_count      BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_videos_customer_id ON videos(customer_id);
CREATE INDEX idx_videos_status ON videos(status) WHERE deleted_at IS NULL;

-- video_play_records
CREATE TABLE video_play_records (
    id              BIGSERIAL PRIMARY KEY,
    video_id        BIGINT NOT NULL REFERENCES videos(id),
    customer_id     BIGINT REFERENCES customers(id),
    play_type       SMALLINT NOT NULL,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_play_records_video ON video_play_records(video_id);

-- video_likes
CREATE TABLE video_likes (
    id              BIGSERIAL PRIMARY KEY,
    video_id        BIGINT NOT NULL REFERENCES videos(id),
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(video_id, customer_id)
);

-- user_follows
CREATE TABLE user_follows (
    id              BIGSERIAL PRIMARY KEY,
    follower_id     BIGINT NOT NULL REFERENCES customers(id),
    followed_id     BIGINT NOT NULL REFERENCES customers(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(follower_id, followed_id),
    CHECK(follower_id != followed_id)
);
CREATE INDEX idx_follows_followed ON user_follows(followed_id);

-- task_definitions
CREATE TABLE task_definitions (
    id              BIGSERIAL PRIMARY KEY,
    task_code       VARCHAR(100) UNIQUE NOT NULL,
    task_name       VARCHAR(255) NOT NULL,
    description     TEXT,
    task_type       SMALLINT NOT NULL,
    task_category   SMALLINT NOT NULL,
    target_count    INT NOT NULL,
    reward_diamonds INT NOT NULL,
    icon_url        VARCHAR(500),
    display_order   INT NOT NULL DEFAULT 0,
    vip_only        BOOLEAN NOT NULL DEFAULT FALSE,
    status          SMALLINT NOT NULL DEFAULT 1,
    start_time      TIMESTAMPTZ,
    end_time        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- task_progress
CREATE TABLE task_progress (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    task_id         BIGINT NOT NULL REFERENCES task_definitions(id),
    current_count   INT NOT NULL DEFAULT 0,
    target_count    INT NOT NULL,
    period_key      VARCHAR(50) NOT NULL,
    task_status     SMALLINT NOT NULL DEFAULT 1,
    reward_diamonds INT NOT NULL,
    completed_at    TIMESTAMPTZ,
    claimed_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(customer_id, task_id, period_key)
);
CREATE INDEX idx_task_progress_customer ON task_progress(customer_id);

-- +goose Down
DROP TABLE IF EXISTS task_progress;
DROP TABLE IF EXISTS task_definitions;
DROP TABLE IF EXISTS user_follows;
DROP TABLE IF EXISTS video_likes;
DROP TABLE IF EXISTS video_play_records;
DROP TABLE IF EXISTS videos;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS admin_users;
```

**Step 2: Run migration against Supabase**

Run: `source .env && make migrate`
Expected: OK, all 8 tables created

**Step 3: Verify**

Run: `make migrate-status`
Expected: 001_init.sql — Applied

**Step 4: Commit**

```bash
git add db/migrations/
git commit -m "feat(db): add initial migration with 8 tables"
```

---

## Task 6: sqlc configuration + auth queries

**Files:**
- Create: `sqlc.yaml`
- Create: `db/queries/customer.sql`
- Create: `db/queries/admin_user.sql`

**Step 1: Create sqlc.yaml**

```yaml
version: "2"
sql:
  - engine: "postgresql"
    queries: "db/queries"
    schema: "db/migrations"
    gen:
      go:
        package: "repository"
        out: "internal/repository"
        sql_package: "pgx/v5"
        emit_json_tags: true
        emit_empty_slices: true
        overrides:
          - db_type: "timestamptz"
            go_type:
              import: "time"
              type: "Time"
              pointer: true
          - db_type: "bigint"
            go_type: "int64"
```

**Step 2: Create customer queries**

```sql
-- db/queries/customer.sql

-- name: GetCustomerByUsername :one
SELECT * FROM customers
WHERE LOWER(username) = LOWER(@username) AND deleted_at IS NULL;

-- name: GetCustomerByID :one
SELECT * FROM customers
WHERE id = @id AND deleted_at IS NULL;

-- name: CreateCustomer :one
INSERT INTO customers (username, password_hash, name)
VALUES (@username, @password_hash, @username)
RETURNING *;

-- name: UpdateCustomerLastLogin :exec
UPDATE customers SET last_login_at = NOW() WHERE id = @id;

-- name: UpdateCustomerPassword :exec
UPDATE customers SET password_hash = @password_hash, updated_at = NOW()
WHERE id = @id AND deleted_at IS NULL;

-- name: GetCustomerProfile :one
SELECT id, username, name, phone, email, avatar_url, signature,
       is_vip, diamond_balance, last_login_at, created_at
FROM customers WHERE id = @id AND deleted_at IS NULL;

-- name: UpdateCustomerProfile :one
UPDATE customers SET
    name = COALESCE(NULLIF(@name, ''), name),
    phone = COALESCE(NULLIF(@phone, ''), phone),
    email = COALESCE(NULLIF(@email, ''), email),
    avatar_url = COALESCE(NULLIF(@avatar_url, ''), avatar_url),
    signature = COALESCE(NULLIF(@signature, ''), signature),
    updated_at = NOW()
WHERE id = @id AND deleted_at IS NULL
RETURNING *;

-- name: AddDiamonds :exec
UPDATE customers SET diamond_balance = diamond_balance + @amount, updated_at = NOW()
WHERE id = @id;
```

**Step 3: Create admin user queries**

```sql
-- db/queries/admin_user.sql

-- name: GetAdminByUsername :one
SELECT * FROM admin_users
WHERE LOWER(username) = LOWER(@username) AND deleted_at IS NULL;

-- name: GetAdminByID :one
SELECT * FROM admin_users
WHERE id = @id AND deleted_at IS NULL;

-- name: CreateAdmin :one
INSERT INTO admin_users (username, password_hash, role, full_name, phone, avatar_url, status)
VALUES (@username, @password_hash, @role, @full_name, @phone, @avatar_url, @status)
RETURNING *;

-- name: UpdateAdminLastLogin :exec
UPDATE admin_users SET last_login_at = NOW() WHERE id = @id;
```

**Step 4: Install pgx and generate code**

Run: `go get github.com/jackc/pgx/v5 && make sqlc`
Expected: files generated in `internal/repository/`

**Step 5: Verify generated code compiles**

Run: `go build ./internal/repository/`
Expected: no errors

**Step 6: Commit**

```bash
git add sqlc.yaml db/queries/ internal/repository/
git commit -m "feat(repo): add sqlc config and auth queries"
```

---

## Task 7: JWT service

**Files:**
- Create: `internal/service/jwt.go`
- Test: `internal/service/jwt_test.go`

**Step 1: Write failing JWT test**

```go
// internal/service/jwt_test.go
package service

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/user/gabon-go/internal/config"
)

func newTestJWTConfig() *config.JWTConfig {
	return &config.JWTConfig{
		CustomerSecret:     "test-customer-secret-min-32-chars!!",
		CustomerAccessTTL:  15 * time.Minute,
		CustomerRefreshTTL: 7 * 24 * time.Hour,
		AdminSecret:        "test-admin-secret-min-32-chars!!!!",
		AdminAccessTTL:     15 * time.Minute,
		AdminRefreshTTL:    7 * 24 * time.Hour,
		CurrentKID:         "test-key-1",
	}
}

func TestJWT_GenerateAndParse_CustomerAccess(t *testing.T) {
	svc := NewJWTService(newTestJWTConfig())
	pair, err := svc.GenerateCustomerTokens(123)
	require.NoError(t, err)
	assert.NotEmpty(t, pair.AccessToken)
	assert.NotEmpty(t, pair.RefreshToken)
	assert.NotEmpty(t, pair.FamilyID)

	claims, err := svc.ParseCustomerToken(pair.AccessToken)
	require.NoError(t, err)
	assert.Equal(t, int64(123), claims.UserID)
	assert.Equal(t, "access", claims.TokenType)
	assert.Equal(t, "gabon-service", claims.Issuer)
	assert.Equal(t, "customer", claims.Audience)
	assert.NotEmpty(t, claims.JTI)
	assert.NotEmpty(t, claims.FamilyID)
}

func TestJWT_ParseCustomerToken_RejectsAdminToken(t *testing.T) {
	svc := NewJWTService(newTestJWTConfig())
	pair, err := svc.GenerateAdminTokens(1, "admin")
	require.NoError(t, err)

	_, err = svc.ParseCustomerToken(pair.AccessToken)
	assert.Error(t, err)
}
```

**Step 2: Run test to verify it fails**

Run: `go test ./internal/service/ -v -run TestJWT`
Expected: FAIL

**Step 3: Write JWT service implementation**

```go
// internal/service/jwt.go
package service

import (
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"github.com/user/gabon-go/internal/config"
)

type TokenPair struct {
	AccessToken  string
	RefreshToken string
	FamilyID     string
	AccessExp    time.Time
	RefreshExp   time.Time
}

type TokenClaims struct {
	UserID    int64
	JTI       string
	FamilyID  string
	TokenType string // "access" or "refresh"
	Issuer    string
	Audience  string
	Role      string
	ExpiresAt time.Time
}

type customClaims struct {
	jwt.RegisteredClaims
	TokenType string `json:"token_type"`
	FamilyID  string `json:"family_id"`
	Role      string `json:"role,omitempty"`
}

type JWTService struct {
	cfg *config.JWTConfig
}

func NewJWTService(cfg *config.JWTConfig) *JWTService {
	return &JWTService{cfg: cfg}
}

func (s *JWTService) GenerateCustomerTokens(customerID int64) (*TokenPair, error) {
	familyID := uuid.New().String()
	return s.generatePair(
		customerID, "", familyID,
		"gabon-service", "customer",
		s.cfg.CustomerSecret,
		s.cfg.CustomerAccessTTL, s.cfg.CustomerRefreshTTL,
	)
}

func (s *JWTService) GenerateAdminTokens(adminID int64, role string) (*TokenPair, error) {
	familyID := uuid.New().String()
	return s.generatePair(
		adminID, role, familyID,
		"gabon-admin", "admin",
		s.cfg.AdminSecret,
		s.cfg.AdminAccessTTL, s.cfg.AdminRefreshTTL,
	)
}

func (s *JWTService) RefreshCustomerTokens(customerID int64, familyID string) (*TokenPair, error) {
	return s.generatePair(
		customerID, "", familyID,
		"gabon-service", "customer",
		s.cfg.CustomerSecret,
		s.cfg.CustomerAccessTTL, s.cfg.CustomerRefreshTTL,
	)
}

func (s *JWTService) ParseCustomerToken(tokenStr string) (*TokenClaims, error) {
	return s.parseToken(tokenStr, s.cfg.CustomerSecret, "gabon-service", "customer")
}

func (s *JWTService) ParseAdminToken(tokenStr string) (*TokenClaims, error) {
	return s.parseToken(tokenStr, s.cfg.AdminSecret, "gabon-admin", "admin")
}

func (s *JWTService) generatePair(
	userID int64, role, familyID, iss, aud, secret string,
	accessTTL, refreshTTL time.Duration,
) (*TokenPair, error) {
	now := time.Now()
	accessExp := now.Add(accessTTL)
	refreshExp := now.Add(refreshTTL)

	accessToken, err := s.signToken(userID, role, familyID, iss, aud, secret, "access", now, accessExp)
	if err != nil {
		return nil, fmt.Errorf("sign access token: %w", err)
	}

	refreshToken, err := s.signToken(userID, role, familyID, iss, aud, secret, "refresh", now, refreshExp)
	if err != nil {
		return nil, fmt.Errorf("sign refresh token: %w", err)
	}

	return &TokenPair{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		FamilyID:     familyID,
		AccessExp:    accessExp,
		RefreshExp:   refreshExp,
	}, nil
}

func (s *JWTService) signToken(
	userID int64, role, familyID, iss, aud, secret, tokenType string,
	now, exp time.Time,
) (string, error) {
	claims := customClaims{
		RegisteredClaims: jwt.RegisteredClaims{
			ID:        uuid.New().String(),
			Issuer:    iss,
			Audience:  jwt.ClaimStrings{aud},
			Subject:   fmt.Sprintf("%d", userID),
			ExpiresAt: jwt.NewNumericDate(exp),
			IssuedAt:  jwt.NewNumericDate(now),
		},
		TokenType: tokenType,
		FamilyID:  familyID,
		Role:      role,
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	token.Header["kid"] = s.cfg.CurrentKID
	return token.SignedString([]byte(secret))
}

func (s *JWTService) parseToken(tokenStr, secret, expectedIss, expectedAud string) (*TokenClaims, error) {
	token, err := jwt.ParseWithClaims(tokenStr, &customClaims{}, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return []byte(secret), nil
	},
		jwt.WithIssuer(expectedIss),
		jwt.WithAudience(expectedAud),
		jwt.WithExpirationRequired(),
	)
	if err != nil {
		return nil, err
	}

	cc, ok := token.Claims.(*customClaims)
	if !ok || !token.Valid {
		return nil, fmt.Errorf("invalid token claims")
	}

	var userID int64
	_, _ = fmt.Sscanf(cc.Subject, "%d", &userID)

	return &TokenClaims{
		UserID:    userID,
		JTI:       cc.ID,
		FamilyID:  cc.FamilyID,
		TokenType: cc.TokenType,
		Issuer:    cc.Issuer,
		Audience:  expectedAud,
		Role:      cc.Role,
		ExpiresAt: cc.ExpiresAt.Time,
	}, nil
}
```

**Step 4: Install jwt dependency and run tests**

Run: `go get github.com/golang-jwt/jwt/v5 && go test ./internal/service/ -v -run TestJWT`
Expected: PASS

**Step 5: Run lint**

Run: `make lint`
Expected: no issues

**Step 6: Commit**

```bash
git add internal/service/jwt.go internal/service/jwt_test.go
git commit -m "feat(auth): add JWT service with domain isolation"
```

---

## Task 8: Auth service + handler + router + main.go

**Files:**
- Create: `internal/service/auth.go`
- Create: `internal/transport/auth_handler.go`
- Create: `internal/transport/router.go`
- Create: `internal/transport/middleware/jwt.go`
- Create: `cmd/api/main.go`
- Test: `internal/service/auth_test.go`

This is the largest task. It wires everything together: register, login, logout, refresh, me, change password.

**Step 1: Write auth service interface**

```go
// internal/service/auth.go (interface only, at top)
package service

import "context"

type AuthService interface {
	Register(ctx context.Context, username, password string) (*TokenPair, error)
	Login(ctx context.Context, username, password string) (*TokenPair, error)
	Logout(ctx context.Context, claims *TokenClaims) error
	Refresh(ctx context.Context, refreshToken string) (*TokenPair, error)
	GetMe(ctx context.Context, customerID int64) (*CustomerInfo, error)
	ChangePassword(ctx context.Context, customerID int64, oldPwd, newPwd string) error
}

type CustomerInfo struct {
	ID       int64  `json:"id"`
	Username string `json:"username"`
	Name     string `json:"name"`
	Phone    string `json:"phone"`
	IsVip    bool   `json:"is_vip"`
	AvatarURL string `json:"avatar_url"`
}
```

**Step 2: Write auth service tests against the interface**

```go
// internal/service/auth_test.go
// Test Register, Login, Refresh, Logout flows
// using mock repository (define repo interface)
```

**Step 3: Implement auth service with bcrypt + Redis token family**

**Step 4: Write JWT auth middleware**

```go
// internal/transport/middleware/jwt.go
// RequireAuth() — must have valid access token
// OptionalAuth() — parse if present, pass through if not
```

**Step 5: Write auth handler binding Echo routes to service**

**Step 6: Write router.go wiring all routes**

**Step 7: Write cmd/api/main.go with manual DI**

**Step 8: Run full test suite**

Run: `make test`
Expected: all tests PASS

**Step 9: Run lint**

Run: `make lint`
Expected: no issues

**Step 10: Manual smoke test**

Run: `make dev` then:
```bash
curl -X POST localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"testuser","password":"Test1234"}'
```
Expected: 200 with access_token + refresh_token

**Step 11: Commit**

```bash
git add cmd/ internal/
git commit -m "feat(auth): add register, login, logout, refresh, me"
```

---

## Task 9-14: Remaining modules (outline)

### Task 9: User module — profile + follow/unfollow

**Files:** `db/queries/user_follow.sql`, `internal/service/user.go`, `internal/transport/user_handler.go`, tests
- Profile CRUD (GET/PUT /users/me/profile)
- Follow/unfollow with INSERT ON CONFLICT
- Following/followers lists with mutual follow detection
- Tests for follow-self rejection, duplicate follow idempotency

### Task 10: Video module — upload mock + CRUD + like

**Files:** `db/queries/video.sql`, `db/queries/video_like.sql`, `db/queries/video_play_record.sql`, service, handler, tests
- Upload URL mock (return fake presigned URL)
- Confirm upload (create video record, status=3 待审核 since mock)
- Video list with pagination, featured (order by like_count)
- Video detail with author info + interaction state
- Like/unlike with CTE atomic counter (from design doc)
- Play click/valid records, valid play triggers task progress
- Concurrency test: parallel likes on same video

### Task 11: Task module — definitions + progress + claim

**Files:** `db/queries/task_definition.sql`, `db/queries/task_progress.sql`, service, handler, tests
- List tasks with auto-assignment on first query per period
- PeriodKey function (Asia/Shanghai, ISO week)
- Progress update triggered by valid play
- Claim reward with FOR UPDATE transaction (from design doc)
- Concurrency test: parallel claim on same progress

### Task 12: Admin module — auth + CRUD + video review

**Files:** `db/queries/admin_user.sql` (extend), service, handler, tests
- Admin auth (separate iss/aud/secret)
- Admin CRUD (superadmin only for create/delete)
- Customer list + password reset
- Video list + review (approve/reject)
- Role middleware checking admin claims

### Task 13: Admin reports

**Files:** extend admin handler + new queries
- Revenue report
- Daily video report
- Video summary report
- All with date range filters and pagination

### Task 14: Quality + CI

**Files:** `.github/workflows/ci.yml`, OpenAPI spec
- golangci-lint in CI
- go test -race in CI
- Integration test setup (test DB)
- OpenAPI 3.0 spec generation or manual spec
- Final README with setup instructions
