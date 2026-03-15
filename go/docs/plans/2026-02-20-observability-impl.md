# Observability Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add structured logging (slog) + OpenTelemetry traces/logs + pprof to gabon-go, shipping to existing Grafana Alloy → Loki → Grafana pipeline.

**Architecture:** OTel SDK initializes TracerProvider + LoggerProvider with OTLP HTTP exporters pointing at Alloy:4318. Echo otelecho middleware auto-captures request spans. slog dual-outputs to console (docker logs) + OTel (Loki via Alloy). pprof on separate port behind env gate.

**Tech Stack:** go.opentelemetry.io/otel, otlptracehttp, otlploghttp, otelslog, otelecho, net/http/pprof

---

### Task 1: Add OTel Dependencies

**Files:**
- Modify: `go.mod`

**Step 1: Install OTel packages**

```bash
cd /Users/ethanwang/projects/gabon-go && go get \
  go.opentelemetry.io/otel \
  go.opentelemetry.io/otel/sdk \
  go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp \
  go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploghttp \
  go.opentelemetry.io/otel/sdk/log \
  go.opentelemetry.io/contrib/bridges/otelslog \
  go.opentelemetry.io/contrib/instrumentation/github.com/labstack/echo/otelecho
```

Note: `go.opentelemetry.io/otel v1.39.0` and `otel/sdk` already exist as indirect deps (from testcontainers). This promotes them to direct and adds the missing exporter/bridge/instrumentation packages.

**Step 2: Verify build**

Run: `go build ./...`
Expected: BUILD SUCCESS, no errors

**Step 3: Commit**

```bash
git add go.mod go.sum
git commit -m "build(deps): add otel trace, log, and echo instrumentation"
```

---

### Task 2: Extend Config with Observability Env Vars

**Files:**
- Modify: `internal/config/config.go`
- Test: `internal/config/config_test.go` (if exists, else skip)

**Step 1: Add OTel and logging fields to Config struct**

Add a new `Observability` sub-struct to `Config`:

```go
type ObservabilityConfig struct {
	LogLevel    string // debug/info/warn/error
	LogFormat   string // text/json
	OTLPEndpoint string // empty = disabled
	ServiceName string
	DeployEnv   string
	EnablePprof bool
	PprofPort   int
}
```

In `Config` struct, add:

```go
type Config struct {
	Port          int
	DatabaseURL   string
	RedisURL      string
	JWT           JWTConfig
	Supabase      SupabaseConfig
	Observability ObservabilityConfig
}
```

**Step 2: Populate in Load()**

At the end of the `Load()` function, before `return`, add:

```go
Observability: ObservabilityConfig{
	LogLevel:     getEnvString("LOG_LEVEL", "info"),
	LogFormat:    getEnvString("LOG_FORMAT", "text"),
	OTLPEndpoint: os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT"),
	ServiceName:  getEnvString("OTEL_SERVICE_NAME", "gabon-api"),
	DeployEnv:    getEnvString("DEPLOY_ENV", "dev"),
	EnablePprof:  os.Getenv("ENABLE_PPROF") == "true",
	PprofPort:    getEnvInt("PPROF_PORT", 6060),
},
```

**Step 3: Verify build**

Run: `go build ./...`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add internal/config/config.go
git commit -m "feat(config): add observability env vars"
```

---

### Task 3: Create OTel Initialization Package

**Files:**
- Create: `internal/observability/otel.go`

This is the core: TracerProvider + LoggerProvider + OTLP exporters + resource + shutdown.

**Step 1: Write the OTel init function**

```go
package observability

import (
	"context"
	"fmt"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploghttp"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/sdk/log"
	"go.opentelemetry.io/otel/sdk/resource"
	"go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.26.0"
)

// SetupOTel configures TracerProvider and LoggerProvider with OTLP HTTP exporters.
// Returns a shutdown function. When endpoint is empty, returns a no-op shutdown.
func SetupOTel(ctx context.Context, endpoint, serviceName, deployEnv string) (logProvider *log.LoggerProvider, shutdown func(context.Context) error, err error) {
	if endpoint == "" {
		return nil, func(context.Context) error { return nil }, nil
	}

	res, err := resource.New(ctx,
		resource.WithAttributes(
			semconv.ServiceNameKey.String(serviceName),
			semconv.DeploymentEnvironmentKey.String(deployEnv),
		),
	)
	if err != nil {
		return nil, nil, fmt.Errorf("create resource: %w", err)
	}

	// Trace exporter
	traceExp, err := otlptracehttp.New(ctx,
		otlptracehttp.WithEndpoint(endpoint),
		otlptracehttp.WithInsecure(),
	)
	if err != nil {
		return nil, nil, fmt.Errorf("create trace exporter: %w", err)
	}

	tp := trace.NewTracerProvider(
		trace.WithBatcher(traceExp),
		trace.WithResource(res),
	)
	otel.SetTracerProvider(tp)

	// Log exporter
	logExp, err := otlploghttp.New(ctx,
		otlploghttp.WithEndpoint(endpoint),
		otlploghttp.WithInsecure(),
	)
	if err != nil {
		return nil, nil, fmt.Errorf("create log exporter: %w", err)
	}

	lp := log.NewLoggerProvider(
		log.WithProcessor(log.NewBatchProcessor(logExp)),
		log.WithResource(res),
	)
	logProvider = lp

	shutdown = func(ctx context.Context) error {
		ctx, cancel := context.WithTimeout(ctx, 5*time.Second)
		defer cancel()
		tpErr := tp.Shutdown(ctx)
		lpErr := lp.Shutdown(ctx)
		if tpErr != nil {
			return tpErr
		}
		return lpErr
	}

	return logProvider, shutdown, nil
}
```

**Step 2: Verify build**

Run: `go build ./...`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add internal/observability/otel.go
git commit -m "feat(core): add otel sdk initialization with otlp exporters"
```

---

### Task 4: Configure slog with Level + Format + OTel Bridge

**Files:**
- Create: `internal/observability/logging.go`

Dual output: console handler (docker logs readable) + OTel bridge (→ Alloy → Loki).

**Step 1: Write the logging setup**

```go
package observability

import (
	"io"
	"log/slog"
	"os"
	"strings"

	"go.opentelemetry.io/contrib/bridges/otelslog"
	sdklog "go.opentelemetry.io/otel/sdk/log"
)

// SetupLogging configures slog with the specified level, format, and optional OTel bridge.
// When logProvider is nil, only console output is configured (no OTel bridge).
func SetupLogging(level, format string, logProvider *sdklog.LoggerProvider) {
	lvl := parseLevel(level)

	consoleHandler := newConsoleHandler(os.Stdout, format, lvl)

	if logProvider == nil {
		slog.SetDefault(slog.New(consoleHandler))
		return
	}

	otelHandler := otelslog.NewHandler("gabon-api", otelslog.WithLoggerProvider(logProvider))
	multi := &multiHandler{handlers: []slog.Handler{consoleHandler, otelHandler}}
	slog.SetDefault(slog.New(multi))
}

func parseLevel(s string) slog.Level {
	switch strings.ToLower(s) {
	case "debug":
		return slog.LevelDebug
	case "warn":
		return slog.LevelWarn
	case "error":
		return slog.LevelError
	default:
		return slog.LevelInfo
	}
}

func newConsoleHandler(w io.Writer, format string, level slog.Level) slog.Handler {
	opts := &slog.HandlerOptions{Level: level}
	if strings.ToLower(format) == "json" {
		return slog.NewJSONHandler(w, opts)
	}
	return slog.NewTextHandler(w, opts)
}

// multiHandler fans out log records to multiple handlers.
type multiHandler struct {
	handlers []slog.Handler
}

func (m *multiHandler) Enabled(_ context.Context, level slog.Level) bool {
	for _, h := range m.handlers {
		if h.Enabled(context.Background(), level) {
			return true
		}
	}
	return false
}

func (m *multiHandler) Handle(ctx context.Context, r slog.Record) error {
	for _, h := range m.handlers {
		if h.Enabled(ctx, r.Level) {
			if err := h.Handle(ctx, r.Clone()); err != nil {
				return err
			}
		}
	}
	return nil
}

func (m *multiHandler) WithAttrs(attrs []slog.Attr) slog.Handler {
	hs := make([]slog.Handler, len(m.handlers))
	for i, h := range m.handlers {
		hs[i] = h.WithAttrs(attrs)
	}
	return &multiHandler{handlers: hs}
}

func (m *multiHandler) WithGroup(name string) slog.Handler {
	hs := make([]slog.Handler, len(m.handlers))
	for i, h := range m.handlers {
		hs[i] = h.WithGroup(name)
	}
	return &multiHandler{handlers: hs}
}
```

Note: 需要在文件顶部 import `"context"`。

**Step 2: Verify build**

Run: `go build ./...`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add internal/observability/logging.go
git commit -m "feat(core): add slog config with level, format, and otel bridge"
```

---

### Task 5: Wire OTel + Logging into main.go

**Files:**
- Modify: `cmd/api/main.go`

**Step 1: Add OTel init + logging setup + otelecho middleware**

In `run()`, after `config.Load()` and before database connection:

```go
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
```

Add import:

```go
"gabon-go/internal/observability"
```

**Step 2: Verify build**

Run: `go build ./...`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add cmd/api/main.go
git commit -m "feat(core): wire otel and structured logging into main"
```

---

### Task 6: Add otelecho Middleware to Router

**Files:**
- Modify: `internal/transport/router.go`

**Step 1: Add otelecho middleware**

In `RegisterRoutes`, add `otelecho.Middleware("gabon-api")` **after** Recovery and RequestID, **before** Logger:

```go
import "go.opentelemetry.io/contrib/instrumentation/github.com/labstack/echo/otelecho"
```

In the middleware registration block:

```go
e.Use(middleware.Recovery())
e.Use(middleware.RequestID())
e.Use(otelecho.Middleware("gabon-api"))  // <-- NEW: auto-captures request spans
e.Use(middleware.Logger())
```

Placing it before Logger ensures the logger middleware can access trace context if needed.

**Step 2: Verify build**

Run: `go build ./...`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add internal/transport/router.go
git commit -m "feat(middleware): add otelecho request span instrumentation"
```

---

### Task 7: Add pprof Debug Endpoint

**Files:**
- Modify: `cmd/api/main.go`

**Step 1: Add pprof server behind env gate**

In `run()`, after observability setup, before database connection:

```go
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
```

Add import `"net/http/pprof"` with blank identifier (side-effect registration):

```go
_ "net/http/pprof"
```

Note: `net/http` is already imported. The blank import of `net/http/pprof` registers `/debug/pprof/` handlers on `http.DefaultServeMux`.

**Step 2: Verify pprof doesn't start by default**

Run: `ENABLE_PPROF= go build ./...`
Expected: BUILD SUCCESS. pprof only starts when `ENABLE_PPROF=true`.

**Step 3: Commit**

```bash
git add cmd/api/main.go
git commit -m "feat(core): add pprof debug endpoint behind env gate"
```

---

### Task 8: Unit Test — Logging Setup

**Files:**
- Create: `internal/observability/logging_test.go`

**Step 1: Write test for parseLevel and SetupLogging**

```go
package observability

import (
	"bytes"
	"log/slog"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestParseLevel(t *testing.T) {
	tests := []struct {
		input string
		want  slog.Level
	}{
		{"debug", slog.LevelDebug},
		{"DEBUG", slog.LevelDebug},
		{"info", slog.LevelInfo},
		{"warn", slog.LevelWarn},
		{"error", slog.LevelError},
		{"unknown", slog.LevelInfo},
		{"", slog.LevelInfo},
	}
	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			assert.Equal(t, tt.want, parseLevel(tt.input))
		})
	}
}

func TestNewConsoleHandler_JSON(t *testing.T) {
	var buf bytes.Buffer
	h := newConsoleHandler(&buf, "json", slog.LevelInfo)
	logger := slog.New(h)
	logger.Info("test message", "key", "value")

	output := buf.String()
	assert.Contains(t, output, `"msg":"test message"`)
	assert.Contains(t, output, `"key":"value"`)
}

func TestNewConsoleHandler_Text(t *testing.T) {
	var buf bytes.Buffer
	h := newConsoleHandler(&buf, "text", slog.LevelInfo)
	logger := slog.New(h)
	logger.Info("test message")

	output := buf.String()
	assert.Contains(t, output, "test message")
}

func TestSetupLogging_NoProvider(t *testing.T) {
	SetupLogging("info", "text", nil)
	// Should not panic, slog.Default() should work
	slog.Info("test after setup")
}
```

**Step 2: Run tests**

Run: `go test -v -race ./internal/observability/...`
Expected: ALL PASS

**Step 3: Commit**

```bash
git add internal/observability/logging_test.go
git commit -m "test(core): add observability logging unit tests"
```

---

### Task 9: Unit Test — OTel Setup

**Files:**
- Create: `internal/observability/otel_test.go`

**Step 1: Write test for no-op when endpoint empty**

```go
package observability

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestSetupOTel_EmptyEndpoint(t *testing.T) {
	lp, shutdown, err := SetupOTel(context.Background(), "", "test-svc", "test")
	require.NoError(t, err)
	assert.Nil(t, lp)
	assert.NotNil(t, shutdown)
	assert.NoError(t, shutdown(context.Background()))
}
```

**Step 2: Run tests**

Run: `go test -v -race ./internal/observability/...`
Expected: ALL PASS

**Step 3: Commit**

```bash
git add internal/observability/otel_test.go
git commit -m "test(core): add otel setup unit test"
```

---

### Task 10: Full Build + Lint Verification

**Files:** None (verification only)

**Step 1: Run linter**

Run: `make lint`
Expected: PASS, no issues

**Step 2: Run all unit tests**

Run: `make test`
Expected: ALL PASS

**Step 3: Docker build**

Run: `make docker-build`
Expected: BUILD SUCCESS

**Step 4: Final commit (if any lint fixes needed)**

Only if Task 10 requires changes. Otherwise, no commit needed.

---

## Verification Checklist

After all tasks complete:

| Check | Command | Expected |
|-------|---------|----------|
| Build | `go build ./...` | SUCCESS |
| Lint | `make lint` | PASS |
| Unit tests | `make test` | ALL PASS |
| Docker build | `make docker-build` | SUCCESS |
| Local dev (no OTEL) | `make dev` | Starts, logs to console, no OTel errors |
| pprof disabled | `make dev` | No pprof listener on :6060 |

## Env Var Summary

| Var | Default | Description |
|-----|---------|-------------|
| `LOG_LEVEL` | `info` | slog level: debug/info/warn/error |
| `LOG_FORMAT` | `text` | Console format: text/json |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | (empty) | Alloy URL (e.g. `http://18.163.149.53:4318`), empty = disabled |
| `OTEL_SERVICE_NAME` | `gabon-api` | OTel resource service.name |
| `DEPLOY_ENV` | `dev` | deployment.environment attribute |
| `ENABLE_PPROF` | `false` | Enable pprof debug endpoint |
| `PPROF_PORT` | `6060` | pprof listener port |
