#!/bin/bash
# Single-endpoint throughput benchmark: Go vs Rust
# Usage: ./bench/oha-endpoints.sh [duration] [concurrency_levels]
# Example: ./bench/oha-endpoints.sh 30 "50 200 500 1000"

set -e

DURATION=${1:-30}
CONCURRENCY_LEVELS=${2:-"50 200 500 1000"}
GO_BASE="http://localhost:8080"
RUST_BASE="http://localhost:3000"

# Seed: register + login to get tokens
echo "=== Seeding test data ==="
GO_TOKEN=$(curl -s -X POST "$GO_BASE/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"benchuser_go","password":"Bench1234!"}' | jq -r '.data.accessToken // empty')

if [ -z "$GO_TOKEN" ]; then
  GO_TOKEN=$(curl -s -X POST "$GO_BASE/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"benchuser_go","password":"Bench1234!"}' | jq -r '.data.accessToken // empty')
fi

RUST_TOKEN=$(curl -s -X POST "$RUST_BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"benchuser_rust","password":"Bench1234!"}' | jq -r '.data.accessToken // empty')

if [ -z "$RUST_TOKEN" ]; then
  RUST_TOKEN=$(curl -s -X POST "$RUST_BASE/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"benchuser_rust","password":"Bench1234!"}' | jq -r '.data.accessToken // empty')
fi

echo "Go token:   ${GO_TOKEN:0:20}..."
echo "Rust token: ${RUST_TOKEN:0:20}..."
echo ""

# Endpoint definitions: name|method|go_path|rust_path|extra_headers
ENDPOINTS=(
  "health|GET|/health|/health|"
  "video_list|GET|/api/v1/videos|/api/videos|"
  "login|POST|/api/v1/auth/login|/api/auth/login|-d {\"username\":\"benchuser_go\",\"password\":\"Bench1234!\"}|-d {\"username\":\"benchuser_rust\",\"password\":\"Bench1234!\"}"
)

run_oha() {
  local label=$1 url=$2 method=$3 duration=$4 concurrency=$5 extra=$6

  echo "  $label ($method $url) c=$concurrency d=${duration}s"

  if [ "$method" = "POST" ] && [ -n "$extra" ]; then
    oha -z "${duration}s" -c "$concurrency" -m "$method" \
      -H "Content-Type: application/json" \
      -d "$extra" \
      "$url" 2>&1 | grep -E "Requests/sec|Slowest|Average|p50|p90|p99"
  else
    oha -z "${duration}s" -c "$concurrency" -m "$method" \
      "$url" 2>&1 | grep -E "Requests/sec|Slowest|Average|p50|p90|p99"
  fi
  echo ""
}

for CONCURRENCY in $CONCURRENCY_LEVELS; do
  echo "============================================"
  echo "  CONCURRENCY: $CONCURRENCY | DURATION: ${DURATION}s"
  echo "============================================"

  # Health (no DB)
  echo ""
  echo "--- health (no DB) ---"
  echo "[Go]"
  run_oha "Go" "$GO_BASE/health" "GET" "$DURATION" "$CONCURRENCY"
  echo "[Rust]"
  run_oha "Rust" "$RUST_BASE/health" "GET" "$DURATION" "$CONCURRENCY"

  # Video list (DB read)
  echo "--- video_list (DB read) ---"
  echo "[Go]"
  run_oha "Go" "$GO_BASE/api/v1/videos" "GET" "$DURATION" "$CONCURRENCY"
  echo "[Rust]"
  run_oha "Rust" "$RUST_BASE/api/videos" "GET" "$DURATION" "$CONCURRENCY"

  echo ""
done

echo "=== Benchmark complete ==="
