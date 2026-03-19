#!/bin/bash
# Single-endpoint throughput benchmark: Go vs Rust vs Kotlin vs Java
# Usage: ./bench/oha-endpoints.sh [duration] [concurrency_levels]
# Example: ./bench/oha-endpoints.sh 30 "50 200 500 1000"

set -e

DURATION=${1:-30}
CONCURRENCY_LEVELS=${2:-"50 200 500 1000"}
GO_BASE="http://localhost:8080"
RUST_BASE="http://localhost:3000"
KOTLIN_BASE="http://localhost:8090"
JAVA_BASE="http://localhost:8082"

# Detect which services are running
GO_UP=false; RUST_UP=false; KT_UP=false; JAVA_UP=false
curl -sf "$GO_BASE/health" >/dev/null 2>&1 && GO_UP=true
curl -sf "$RUST_BASE/health" >/dev/null 2>&1 && RUST_UP=true
curl -sf "$KOTLIN_BASE/health" >/dev/null 2>&1 && KT_UP=true
curl -sf "$JAVA_BASE/health" >/dev/null 2>&1 && JAVA_UP=true

echo "=== Services: Go=$GO_UP  Rust=$RUST_UP  Kotlin=$KT_UP  Java=$JAVA_UP ==="
echo ""

# Seed: register + login to get tokens
echo "=== Seeding test data ==="
if [ "$GO_UP" = true ]; then
  GO_TOKEN=$(curl -s -X POST "$GO_BASE/api/v1/auth/register" \
    -H 'Content-Type: application/json' \
    -d '{"username":"benchuser_go","password":"Bench1234!"}' | jq -r '.data.accessToken // empty')
  if [ -z "$GO_TOKEN" ]; then
    GO_TOKEN=$(curl -s -X POST "$GO_BASE/api/v1/auth/login" \
      -H 'Content-Type: application/json' \
      -d '{"username":"benchuser_go","password":"Bench1234!"}' | jq -r '.data.accessToken // empty')
  fi
  echo "Go token:     ${GO_TOKEN:0:20}..."
fi

if [ "$RUST_UP" = true ]; then
  RUST_TOKEN=$(curl -s -X POST "$RUST_BASE/api/auth/register" \
    -H 'Content-Type: application/json' \
    -d '{"username":"benchuser_rust","password":"Bench1234!"}' | jq -r '.data.accessToken // empty')
  if [ -z "$RUST_TOKEN" ]; then
    RUST_TOKEN=$(curl -s -X POST "$RUST_BASE/api/auth/login" \
      -H 'Content-Type: application/json' \
      -d '{"username":"benchuser_rust","password":"Bench1234!"}' | jq -r '.data.accessToken // empty')
  fi
  echo "Rust token:   ${RUST_TOKEN:0:20}..."
fi

if [ "$KT_UP" = true ]; then
  KT_TOKEN=$(curl -s -X POST "$KOTLIN_BASE/api/v1/auth/register" \
    -H 'Content-Type: application/json' \
    -d '{"username":"benchuser_kt","password":"Bench1234!"}' | jq -r '.data.accessToken // empty')
  if [ -z "$KT_TOKEN" ]; then
    KT_TOKEN=$(curl -s -X POST "$KOTLIN_BASE/api/v1/auth/login" \
      -H 'Content-Type: application/json' \
      -d '{"username":"benchuser_kt","password":"Bench1234!"}' | jq -r '.data.accessToken // empty')
  fi
  echo "Kotlin token: ${KT_TOKEN:0:20}..."
fi

if [ "$JAVA_UP" = true ]; then
  JAVA_TOKEN=$(curl -s -X POST "$JAVA_BASE/api/v1/auth/register" \
    -H 'Content-Type: application/json' \
    -d '{"username":"benchuser_java","password":"Bench1234!"}' | jq -r '.data.accessToken // empty')
  if [ -z "$JAVA_TOKEN" ]; then
    JAVA_TOKEN=$(curl -s -X POST "$JAVA_BASE/api/v1/auth/login" \
      -H 'Content-Type: application/json' \
      -d '{"username":"benchuser_java","password":"Bench1234!"}' | jq -r '.data.accessToken // empty')
  fi
  echo "Java token:   ${JAVA_TOKEN:0:20}..."
fi

echo ""

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
  if [ "$GO_UP" = true ]; then
    echo "[Go]"
    run_oha "Go" "$GO_BASE/health" "GET" "$DURATION" "$CONCURRENCY"
  fi
  if [ "$RUST_UP" = true ]; then
    echo "[Rust]"
    run_oha "Rust" "$RUST_BASE/health" "GET" "$DURATION" "$CONCURRENCY"
  fi
  if [ "$KT_UP" = true ]; then
    echo "[Kotlin]"
    run_oha "Kotlin" "$KOTLIN_BASE/health" "GET" "$DURATION" "$CONCURRENCY"
  fi
  if [ "$JAVA_UP" = true ]; then
    echo "[Java]"
    run_oha "Java" "$JAVA_BASE/health" "GET" "$DURATION" "$CONCURRENCY"
  fi

  # Video list (DB read)
  echo "--- video_list (DB read) ---"
  if [ "$GO_UP" = true ]; then
    echo "[Go]"
    run_oha "Go" "$GO_BASE/api/v1/videos" "GET" "$DURATION" "$CONCURRENCY"
  fi
  if [ "$RUST_UP" = true ]; then
    echo "[Rust]"
    run_oha "Rust" "$RUST_BASE/api/videos" "GET" "$DURATION" "$CONCURRENCY"
  fi
  if [ "$KT_UP" = true ]; then
    echo "[Kotlin]"
    run_oha "Kotlin" "$KOTLIN_BASE/api/v1/videos" "GET" "$DURATION" "$CONCURRENCY"
  fi
  if [ "$JAVA_UP" = true ]; then
    echo "[Java]"
    run_oha "Java" "$JAVA_BASE/api/v1/videos" "GET" "$DURATION" "$CONCURRENCY"
  fi

  echo ""
done

echo "=== Benchmark complete ==="
