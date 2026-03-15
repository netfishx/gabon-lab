package observability

import (
	"context"
	"errors"
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
// Returns a log provider (for slog bridge) and shutdown function.
// When endpoint is empty, returns nil provider and no-op shutdown.
func SetupOTel(ctx context.Context, endpoint, serviceName, deployEnv string) (logProvider *log.LoggerProvider, shutdown func(context.Context) error, err error) {
	if endpoint == "" {
		return nil, func(context.Context) error { return nil }, nil
	}

	res, err := resource.New(ctx,
		resource.WithTelemetrySDK(),
		resource.WithAttributes(
			semconv.ServiceName(serviceName),
			semconv.DeploymentEnvironment(deployEnv),
		),
	)
	if err != nil {
		return nil, nil, fmt.Errorf("create resource: %w", err)
	}

	// Trace exporter
	traceExp, err := otlptracehttp.New(ctx,
		otlptracehttp.WithEndpointURL(endpoint),
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
		otlploghttp.WithEndpointURL(endpoint),
	)
	if err != nil {
		_ = tp.Shutdown(ctx)
		return nil, nil, fmt.Errorf("create log exporter: %w", err)
	}

	lp := log.NewLoggerProvider(
		log.WithProcessor(log.NewBatchProcessor(logExp)),
		log.WithResource(res),
	)

	shutdown = func(ctx context.Context) error {
		ctx, cancel := context.WithTimeout(ctx, 5*time.Second)
		defer cancel()
		tpErr := tp.Shutdown(ctx)
		lpErr := lp.Shutdown(ctx)
		return errors.Join(tpErr, lpErr)
	}

	return lp, shutdown, nil
}
