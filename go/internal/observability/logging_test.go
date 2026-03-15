package observability

import (
	"bytes"
	"context"
	"encoding/json"
	"log/slog"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestParseLevel(t *testing.T) {
	tests := []struct {
		name  string
		input string
		want  slog.Level
	}{
		{name: "debug lowercase", input: "debug", want: slog.LevelDebug},
		{name: "DEBUG uppercase", input: "DEBUG", want: slog.LevelDebug},
		{name: "Debug mixed", input: "Debug", want: slog.LevelDebug},
		{name: "info lowercase", input: "info", want: slog.LevelInfo},
		{name: "INFO uppercase", input: "INFO", want: slog.LevelInfo},
		{name: "warn lowercase", input: "warn", want: slog.LevelWarn},
		{name: "WARN uppercase", input: "WARN", want: slog.LevelWarn},
		{name: "error lowercase", input: "error", want: slog.LevelError},
		{name: "ERROR uppercase", input: "ERROR", want: slog.LevelError},
		{name: "unknown defaults to info", input: "unknown", want: slog.LevelInfo},
		{name: "empty defaults to info", input: "", want: slog.LevelInfo},
		{name: "random string defaults to info", input: "foobar", want: slog.LevelInfo},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := parseLevel(tt.input)
			assert.Equal(t, tt.want, got)
		})
	}
}

func TestNewConsoleHandler_JSON(t *testing.T) {
	var buf bytes.Buffer
	h := newConsoleHandler(&buf, "json", slog.LevelInfo)

	logger := slog.New(h)
	logger.Info("test message", "key1", "value1", "key2", 42)

	output := buf.String()
	require.NotEmpty(t, output, "JSON handler should produce output")

	// Verify it is valid JSON.
	var parsed map[string]any
	err := json.Unmarshal([]byte(output), &parsed)
	require.NoError(t, err, "output should be valid JSON")

	// slog.JSONHandler uses "msg" as the message key.
	assert.Equal(t, "test message", parsed["msg"])
	assert.Equal(t, "value1", parsed["key1"])
	assert.Equal(t, float64(42), parsed["key2"]) // JSON numbers decode as float64
}

func TestNewConsoleHandler_JSON_CaseInsensitive(t *testing.T) {
	var buf bytes.Buffer
	h := newConsoleHandler(&buf, "JSON", slog.LevelInfo)

	logger := slog.New(h)
	logger.Info("json test")

	output := buf.String()
	require.NotEmpty(t, output)

	var parsed map[string]any
	err := json.Unmarshal([]byte(output), &parsed)
	require.NoError(t, err, "JSON format should be recognized case-insensitively")
	assert.Equal(t, "json test", parsed["msg"])
}

func TestNewConsoleHandler_Text(t *testing.T) {
	var buf bytes.Buffer
	h := newConsoleHandler(&buf, "text", slog.LevelInfo)

	logger := slog.New(h)
	logger.Info("hello world", "component", "test")

	output := buf.String()
	require.NotEmpty(t, output, "text handler should produce output")
	assert.Contains(t, output, "hello world")
	assert.Contains(t, output, "component=test")
}

func TestNewConsoleHandler_DefaultText(t *testing.T) {
	// Non-"json" format should default to text handler.
	var buf bytes.Buffer
	h := newConsoleHandler(&buf, "pretty", slog.LevelInfo)

	logger := slog.New(h)
	logger.Info("default format")

	output := buf.String()
	require.NotEmpty(t, output)
	assert.Contains(t, output, "default format")

	// Should NOT be valid JSON (it's text format).
	var parsed map[string]any
	err := json.Unmarshal([]byte(output), &parsed)
	assert.Error(t, err, "non-json format should produce text output, not JSON")
}

func TestNewConsoleHandler_RespectsLevel(t *testing.T) {
	var buf bytes.Buffer
	h := newConsoleHandler(&buf, "text", slog.LevelWarn)

	logger := slog.New(h)
	logger.Info("should be filtered")

	assert.Empty(t, buf.String(), "info message should be filtered by warn-level handler")

	logger.Warn("should appear")
	assert.NotEmpty(t, buf.String(), "warn message should pass through warn-level handler")
}

func TestSetupLogging_NoProvider(t *testing.T) {
	// Save and restore original default logger.
	original := slog.Default()
	t.Cleanup(func() { slog.SetDefault(original) })

	// Should not panic with nil provider.
	require.NotPanics(t, func() {
		SetupLogging("info", "text", nil)
	})

	// Verify the default logger is functional.
	require.NotPanics(t, func() {
		slog.Info("test log after setup", "key", "value")
	})
}

func TestLevelHandler_FiltersLowLevel(t *testing.T) {
	var buf bytes.Buffer
	inner := newConsoleHandler(&buf, "text", slog.LevelDebug) // inner accepts all
	filtered := &levelHandler{level: slog.LevelError, inner: inner}

	logger := slog.New(filtered)
	logger.Info("should be filtered")
	assert.Empty(t, buf.String(), "info should be blocked by error-level gate")

	logger.Error("should pass")
	assert.Contains(t, buf.String(), "should pass")
}

func TestLevelHandler_WithAttrsPreservesLevel(t *testing.T) {
	var buf bytes.Buffer
	inner := newConsoleHandler(&buf, "text", slog.LevelDebug)
	filtered := &levelHandler{level: slog.LevelWarn, inner: inner}

	withAttrs := filtered.WithAttrs([]slog.Attr{slog.String("k", "v")})
	logger := slog.New(withAttrs)
	logger.Info("filtered")
	assert.Empty(t, buf.String())

	logger.Warn("passed")
	assert.Contains(t, buf.String(), "passed")
	assert.Contains(t, buf.String(), "k=v")
}

func TestMultiHandler_OTelChannelRespectsLevel(t *testing.T) {
	// Simulates the real setup: console (error) + "otel" (accepts all internally).
	// levelHandler should gate the otel channel to the same level.
	var consoleBuf, otelBuf bytes.Buffer
	consoleH := newConsoleHandler(&consoleBuf, "text", slog.LevelError)
	otelInner := newConsoleHandler(&otelBuf, "json", slog.LevelDebug) // simulates otelslog accepting all
	otelFiltered := &levelHandler{level: slog.LevelError, inner: otelInner}

	multi := &multiHandler{handlers: []slog.Handler{consoleH, otelFiltered}}
	logger := slog.New(multi)

	logger.Info("info msg")
	assert.Empty(t, consoleBuf.String(), "console should filter info")
	assert.Empty(t, otelBuf.String(), "otel channel should also filter info")

	logger.Error("error msg")
	assert.Contains(t, consoleBuf.String(), "error msg")
	assert.Contains(t, otelBuf.String(), "error msg")
}

func TestMultiHandler_Enabled(t *testing.T) {
	var buf1, buf2 bytes.Buffer
	h1 := newConsoleHandler(&buf1, "text", slog.LevelWarn)
	h2 := newConsoleHandler(&buf2, "text", slog.LevelDebug)

	multi := &multiHandler{handlers: []slog.Handler{h1, h2}}

	// Debug should be enabled because h2 accepts it.
	assert.True(t, multi.Enabled(context.Background(), slog.LevelDebug))
	// Warn should be enabled because both accept it.
	assert.True(t, multi.Enabled(context.Background(), slog.LevelWarn))
}

func TestMultiHandler_FanOut(t *testing.T) {
	var buf1, buf2 bytes.Buffer
	h1 := newConsoleHandler(&buf1, "text", slog.LevelInfo)
	h2 := newConsoleHandler(&buf2, "json", slog.LevelInfo)

	multi := &multiHandler{handlers: []slog.Handler{h1, h2}}
	logger := slog.New(multi)
	logger.Info("fan out", "k", "v")

	// Both handlers should receive the record.
	assert.Contains(t, buf1.String(), "fan out")
	assert.Contains(t, buf2.String(), "fan out")
}

func TestMultiHandler_WithAttrs(t *testing.T) {
	var buf bytes.Buffer
	h := newConsoleHandler(&buf, "text", slog.LevelInfo)
	multi := &multiHandler{handlers: []slog.Handler{h}}

	withAttrs := multi.WithAttrs([]slog.Attr{slog.String("static", "attr")})
	logger := slog.New(withAttrs)
	logger.Info("attrs test")

	assert.Contains(t, buf.String(), "static=attr")
}

func TestMultiHandler_WithGroup(t *testing.T) {
	var buf bytes.Buffer
	h := newConsoleHandler(&buf, "json", slog.LevelInfo)
	multi := &multiHandler{handlers: []slog.Handler{h}}

	withGroup := multi.WithGroup("grp")
	logger := slog.New(withGroup)
	logger.Info("group test", "field", "val")

	var parsed map[string]any
	err := json.Unmarshal(buf.Bytes(), &parsed)
	require.NoError(t, err)

	// JSON handler nests grouped attributes under the group name.
	grp, ok := parsed["grp"].(map[string]any)
	require.True(t, ok, "expected group key 'grp' in JSON output")
	assert.Equal(t, "val", grp["field"])
}
