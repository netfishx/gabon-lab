# Observability Design — gabon-go

## Context

Production infra on 18.163.149.53 runs Grafana Alloy → Loki → Grafana.
Alloy accepts OTLP HTTP on :4318, extracts span attributes (http.method,
http.status_code, http.route, duration, traceId), and pushes everything
to Loki with `service.name` label.

orbit frontend apps already emit traces + logs to this pipeline.
gabon-go needs to join the same pipeline.

## Architecture

```
gabon-go (slog + OTel SDK)
    │
    ├── traces (request spans) ──→ OTLP HTTP ──→ Alloy:4318
    └── logs (slog bridge)     ──→ OTLP HTTP ──→ Alloy:4318
                                                     │
                                                     ▼
                                                   Loki
                                                     │
                                                     ▼
                                                  Grafana
                                        (filter: service.name=gabon-api)
```

## Deliverables

### 1. Structured Logging (slog)

- `LOG_LEVEL` env var: debug/info/warn/error (default: info)
- `LOG_FORMAT` env var: text (default) / json
- JSON format for production (machine-parseable)
- Text format for local dev (human-readable)
- Configure in main.go before any other initialization

### 2. OTel SDK Integration

Dependencies:
- `go.opentelemetry.io/otel`
- `go.opentelemetry.io/otel/sdk`
- `go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp`
- `go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploghttp`
- `go.opentelemetry.io/contrib/bridges/otelslog`
- `go.opentelemetry.io/contrib/instrumentation/github.com/labstack/echo/otelecho`

Resource attributes:
- `service.name=gabon-api`
- `deployment.environment` from env var (dev/staging/prod)

Env vars:
- `OTEL_EXPORTER_OTLP_ENDPOINT` — Alloy URL (e.g. http://localhost:4318)
- When empty, OTel is disabled (no-op, safe for local dev)

### 3. Echo OTel Middleware

- `otelecho.Middleware("gabon-api")` registered before all routes
- Auto-captures: http.method, http.route, http.status_code, duration
- Alloy already extracts these attributes into Loki labels

### 4. slog → OTel Logs Bridge

- `otelslog.NewHandler` wraps slog handler
- Application logs (slog.Info/Error etc.) forwarded to OTel LoggerProvider
- Dual output: console (for docker logs) + OTel (for Loki)
- Logs carry trace context (traceId) when inside a request span

### 5. pprof Debug Endpoint

- Register `net/http/pprof` on separate port or behind env gate
- Only enabled when `ENABLE_PPROF=true`
- Access: `GET /debug/pprof/` for heap, goroutine, CPU profiles

## Non-goals

- Prometheus /metrics endpoint (Alloy handles metrics via span logs)
- Custom metrics (request count/latency already captured by traces)
- Jaeger/Zipkin exporters (OTLP HTTP is the standard)
- Log file rotation (containerized, use docker logs)

## Config Summary

| Env Var | Default | Description |
|---------|---------|-------------|
| LOG_LEVEL | info | slog level |
| LOG_FORMAT | text | text or json |
| OTEL_EXPORTER_OTLP_ENDPOINT | (empty) | Alloy URL, empty = disabled |
| OTEL_SERVICE_NAME | gabon-api | OTel resource name |
| DEPLOY_ENV | dev | deployment.environment attribute |
| ENABLE_PPROF | false | Enable pprof debug endpoint |
