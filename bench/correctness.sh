#!/bin/bash
# Correctness verification: same inputs → same outputs
set -e

GO_BASE="http://localhost:8080"
RUST_BASE="http://localhost:3000"
GO_PREFIX="/api/v1"
RUST_PREFIX="/api"
PASS=0
FAIL=0

check() {
  local name=$1 go_status=$2 rust_status=$3 go_has_data=$4 rust_has_data=$5

  if [ "$go_status" = "$rust_status" ] && [ "$go_has_data" = "$rust_has_data" ]; then
    echo "  ✓ $name (Go=$go_status Rust=$rust_status)"
    PASS=$((PASS + 1))
  else
    echo "  ✗ $name (Go=$go_status/$go_has_data Rust=$rust_status/$rust_has_data)"
    FAIL=$((FAIL + 1))
  fi
}

echo "============================================"
echo "  CORRECTNESS: Go vs Rust"
echo "============================================"
echo ""

# Unique users for this run
TS=$(date +%s)
USER_GO="correct_go_$TS"
USER_RUST="correct_rust_$TS"

# 1. Health
echo "--- Health ---"
GO_H=$(curl -s -o /dev/null -w "%{http_code}" "$GO_BASE/health")
RUST_H=$(curl -s -o /dev/null -w "%{http_code}" "$RUST_BASE/health")
check "health" "$GO_H" "$RUST_H" "yes" "yes"

# 2. Register
echo "--- Register ---"
GO_REG=$(curl -s -X POST "$GO_BASE$GO_PREFIX/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER_GO\",\"password\":\"Test1234!\"}")
RUST_REG=$(curl -s -X POST "$RUST_BASE$RUST_PREFIX/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER_RUST\",\"password\":\"Test1234!\"}")

# Compare HTTP status only (response format intentionally differs: Go="OK", Rust=0)
GO_REG_S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GO_BASE$GO_PREFIX/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${USER_GO}_dup\",\"password\":\"Test1234!\"}")
RUST_REG_S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$RUST_BASE$RUST_PREFIX/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${USER_RUST}_dup\",\"password\":\"Test1234!\"}")
check "register" "$GO_REG_S" "$RUST_REG_S" "yes" "yes"

# Extract tokens
GO_TOKEN=$(echo "$GO_REG" | jq -r '.data.accessToken // .data.access_token // empty')
RUST_TOKEN=$(echo "$RUST_REG" | jq -r '.data.accessToken // .data.access_token // empty')

# 3. Login
echo "--- Login ---"
GO_LOGIN=$(curl -s -X POST "$GO_BASE$GO_PREFIX/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER_GO\",\"password\":\"Test1234!\"}")
RUST_LOGIN=$(curl -s -X POST "$RUST_BASE$RUST_PREFIX/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER_RUST\",\"password\":\"Test1234!\"}")

GO_LOGIN_S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GO_BASE$GO_PREFIX/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER_GO\",\"password\":\"Test1234!\"}")
RUST_LOGIN_S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$RUST_BASE$RUST_PREFIX/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER_RUST\",\"password\":\"Test1234!\"}")
check "login" "$GO_LOGIN_S" "$RUST_LOGIN_S" "yes" "yes"

# 4. Login with wrong password
echo "--- Login (wrong password) ---"
GO_BAD=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GO_BASE$GO_PREFIX/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER_GO\",\"password\":\"wrong\"}")
RUST_BAD=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$RUST_BASE$RUST_PREFIX/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER_RUST\",\"password\":\"wrong\"}")
check "login_wrong_pw" "$GO_BAD" "$RUST_BAD" "no" "no"

# 5. Get me (authenticated)
echo "--- Auth Me ---"
GO_ME_S=$(curl -s -o /dev/null -w "%{http_code}" "$GO_BASE$GO_PREFIX/auth/me" \
  -H "Authorization: Bearer $GO_TOKEN")
RUST_ME_S=$(curl -s -o /dev/null -w "%{http_code}" "$RUST_BASE$RUST_PREFIX/auth/me" \
  -H "Authorization: Bearer $RUST_TOKEN")
check "auth_me" "$GO_ME_S" "$RUST_ME_S" "yes" "yes"

# 6. Get me (no token)
echo "--- Auth Me (no token) ---"
GO_NOAUTH=$(curl -s -o /dev/null -w "%{http_code}" "$GO_BASE$GO_PREFIX/auth/me")
RUST_NOAUTH=$(curl -s -o /dev/null -w "%{http_code}" "$RUST_BASE$RUST_PREFIX/auth/me")
check "auth_me_notoken" "$GO_NOAUTH" "$RUST_NOAUTH" "no" "no"

# 7. Video list (public)
echo "--- Video List ---"
GO_VID=$(curl -s -o /dev/null -w "%{http_code}" "$GO_BASE$GO_PREFIX/videos")
RUST_VID=$(curl -s -o /dev/null -w "%{http_code}" "$RUST_BASE$RUST_PREFIX/videos")
check "video_list" "$GO_VID" "$RUST_VID" "yes" "yes"

# 8. Duplicate register
echo "--- Duplicate Register ---"
GO_DUP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GO_BASE$GO_PREFIX/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER_GO\",\"password\":\"Test1234!\"}")
RUST_DUP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$RUST_BASE$RUST_PREFIX/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER_RUST\",\"password\":\"Test1234!\"}")
check "duplicate_register" "$GO_DUP" "$RUST_DUP" "no" "no"

echo ""
echo "============================================"
echo "  RESULTS: $PASS passed, $FAIL failed"
echo "============================================"
