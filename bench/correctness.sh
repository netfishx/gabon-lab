#!/bin/bash
# Correctness verification: same inputs → same outputs
set -e

GO_BASE="http://localhost:8080"
RUST_BASE="http://localhost:3000"
KOTLIN_BASE="http://localhost:8090"
JAVA_BASE="http://localhost:8082"
GO_PREFIX="/api/v1"
RUST_PREFIX="/api"
KOTLIN_PREFIX="/api/v1"
JAVA_PREFIX="/api/v1"
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

check_kt() {
  local name=$1 go_status=$2 kt_status=$3 go_has_data=$4 kt_has_data=$5

  if [ "$go_status" = "$kt_status" ] && [ "$go_has_data" = "$kt_has_data" ]; then
    echo "  ✓ $name (Go=$go_status Kotlin=$kt_status)"
    PASS=$((PASS + 1))
  else
    echo "  ✗ $name (Go=$go_status/$go_has_data Kotlin=$kt_status/$kt_has_data)"
    FAIL=$((FAIL + 1))
  fi
}

check_java() {
  local name=$1 go_status=$2 java_status=$3 go_has_data=$4 java_has_data=$5

  if [ "$go_status" = "$java_status" ] && [ "$go_has_data" = "$java_has_data" ]; then
    echo "  ✓ $name (Go=$go_status Java=$java_status)"
    PASS=$((PASS + 1))
  else
    echo "  ✗ $name (Go=$go_status/$go_has_data Java=$java_status/$java_has_data)"
    FAIL=$((FAIL + 1))
  fi
}

# Detect which services are running
GO_UP=false; RUST_UP=false; KT_UP=false; JAVA_UP=false
curl -sf "$GO_BASE/health" >/dev/null 2>&1 && GO_UP=true
curl -sf "$RUST_BASE/health" >/dev/null 2>&1 && RUST_UP=true
curl -sf "$KOTLIN_BASE/health" >/dev/null 2>&1 && KT_UP=true
curl -sf "$JAVA_BASE/health" >/dev/null 2>&1 && JAVA_UP=true

echo "============================================"
echo "  CORRECTNESS: Go vs Rust vs Kotlin vs Java"
echo "============================================"
echo "  Go=$GO_UP  Rust=$RUST_UP  Kotlin=$KT_UP  Java=$JAVA_UP"
echo ""

if [ "$GO_UP" = false ] && [ "$RUST_UP" = false ] && [ "$KT_UP" = false ] && [ "$JAVA_UP" = false ]; then
  echo "ERROR: No services running. Start at least two services to compare."
  exit 1
fi

# Unique users for this run
TS=$(date +%s)
USER_GO="correct_go_$TS"
USER_RUST="correct_rust_$TS"
USER_KT="correct_kt_$TS"
USER_JAVA="correct_java_$TS"

# ─── Go vs Rust ────────────────────────────────
if [ "$GO_UP" = true ] && [ "$RUST_UP" = true ]; then
  echo "========== Go vs Rust =========="
  echo ""

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
fi

# ─── Go vs Kotlin ──────────────────────────────
if [ "$GO_UP" = true ] && [ "$KT_UP" = true ]; then
  echo "========== Go vs Kotlin =========="
  echo ""

  # 1. Health
  echo "--- Health ---"
  GO_H=$(curl -s -o /dev/null -w "%{http_code}" "$GO_BASE/health")
  KT_H=$(curl -s -o /dev/null -w "%{http_code}" "$KOTLIN_BASE/health")
  check_kt "health" "$GO_H" "$KT_H" "yes" "yes"

  # 2. Register
  echo "--- Register ---"
  GO_REG2=$(curl -s -X POST "$GO_BASE$GO_PREFIX/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${USER_GO}_kt\",\"password\":\"Test1234!\"}")
  KT_REG=$(curl -s -X POST "$KOTLIN_BASE$KOTLIN_PREFIX/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USER_KT\",\"password\":\"Test1234!\"}")

  GO_REG2_S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GO_BASE$GO_PREFIX/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${USER_GO}_kt2\",\"password\":\"Test1234!\"}")
  KT_REG_S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$KOTLIN_BASE$KOTLIN_PREFIX/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${USER_KT}_dup\",\"password\":\"Test1234!\"}")
  check_kt "register" "$GO_REG2_S" "$KT_REG_S" "yes" "yes"

  # Extract tokens
  GO_TOKEN2=$(echo "$GO_REG2" | jq -r '.data.accessToken // .data.access_token // empty')
  KT_TOKEN=$(echo "$KT_REG" | jq -r '.data.accessToken // .data.access_token // empty')

  # 3. Login
  echo "--- Login ---"
  GO_LOGIN2_S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GO_BASE$GO_PREFIX/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${USER_GO}_kt\",\"password\":\"Test1234!\"}")
  KT_LOGIN_S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$KOTLIN_BASE$KOTLIN_PREFIX/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USER_KT\",\"password\":\"Test1234!\"}")
  check_kt "login" "$GO_LOGIN2_S" "$KT_LOGIN_S" "yes" "yes"

  # 4. Login with wrong password
  echo "--- Login (wrong password) ---"
  GO_BAD2=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GO_BASE$GO_PREFIX/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${USER_GO}_kt\",\"password\":\"wrong\"}")
  KT_BAD=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$KOTLIN_BASE$KOTLIN_PREFIX/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USER_KT\",\"password\":\"wrong\"}")
  check_kt "login_wrong_pw" "$GO_BAD2" "$KT_BAD" "no" "no"

  # 5. Get me (authenticated)
  echo "--- Auth Me ---"
  GO_ME2_S=$(curl -s -o /dev/null -w "%{http_code}" "$GO_BASE$GO_PREFIX/auth/me" \
    -H "Authorization: Bearer $GO_TOKEN2")
  KT_ME_S=$(curl -s -o /dev/null -w "%{http_code}" "$KOTLIN_BASE$KOTLIN_PREFIX/auth/me" \
    -H "Authorization: Bearer $KT_TOKEN")
  check_kt "auth_me" "$GO_ME2_S" "$KT_ME_S" "yes" "yes"

  # 6. Get me (no token)
  echo "--- Auth Me (no token) ---"
  GO_NOAUTH2=$(curl -s -o /dev/null -w "%{http_code}" "$GO_BASE$GO_PREFIX/auth/me")
  KT_NOAUTH=$(curl -s -o /dev/null -w "%{http_code}" "$KOTLIN_BASE$KOTLIN_PREFIX/auth/me")
  check_kt "auth_me_notoken" "$GO_NOAUTH2" "$KT_NOAUTH" "no" "no"

  # 7. Video list (public)
  echo "--- Video List ---"
  GO_VID2=$(curl -s -o /dev/null -w "%{http_code}" "$GO_BASE$GO_PREFIX/videos")
  KT_VID=$(curl -s -o /dev/null -w "%{http_code}" "$KOTLIN_BASE$KOTLIN_PREFIX/videos")
  check_kt "video_list" "$GO_VID2" "$KT_VID" "yes" "yes"

  # 8. Duplicate register
  echo "--- Duplicate Register ---"
  GO_DUP2=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GO_BASE$GO_PREFIX/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${USER_GO}_kt\",\"password\":\"Test1234!\"}")
  KT_DUP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$KOTLIN_BASE$KOTLIN_PREFIX/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USER_KT\",\"password\":\"Test1234!\"}")
  check_kt "duplicate_register" "$GO_DUP2" "$KT_DUP" "no" "no"
  echo ""
fi

# ─── Go vs Java ───────────────────────────────
if [ "$GO_UP" = true ] && [ "$JAVA_UP" = true ]; then
  echo "========== Go vs Java =========="
  echo ""

  # 1. Health
  echo "--- Health ---"
  GO_H=$(curl -s -o /dev/null -w "%{http_code}" "$GO_BASE/health")
  JAVA_H=$(curl -s -o /dev/null -w "%{http_code}" "$JAVA_BASE/health")
  check_java "health" "$GO_H" "$JAVA_H" "yes" "yes"

  # 2. Register
  echo "--- Register ---"
  GO_REG3=$(curl -s -X POST "$GO_BASE$GO_PREFIX/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${USER_GO}_java\",\"password\":\"Test1234!\"}")
  JAVA_REG=$(curl -s -X POST "$JAVA_BASE$JAVA_PREFIX/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USER_JAVA\",\"password\":\"Test1234!\"}")

  GO_REG3_S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GO_BASE$GO_PREFIX/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${USER_GO}_java2\",\"password\":\"Test1234!\"}")
  JAVA_REG_S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$JAVA_BASE$JAVA_PREFIX/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${USER_JAVA}_dup\",\"password\":\"Test1234!\"}")
  check_java "register" "$GO_REG3_S" "$JAVA_REG_S" "yes" "yes"

  # Extract tokens
  GO_TOKEN3=$(echo "$GO_REG3" | jq -r '.data.accessToken // .data.access_token // empty')
  JAVA_TOKEN=$(echo "$JAVA_REG" | jq -r '.data.accessToken // .data.access_token // empty')

  # 3. Login
  echo "--- Login ---"
  GO_LOGIN3_S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GO_BASE$GO_PREFIX/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${USER_GO}_java\",\"password\":\"Test1234!\"}")
  JAVA_LOGIN_S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$JAVA_BASE$JAVA_PREFIX/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USER_JAVA\",\"password\":\"Test1234!\"}")
  check_java "login" "$GO_LOGIN3_S" "$JAVA_LOGIN_S" "yes" "yes"

  # 4. Login with wrong password
  echo "--- Login (wrong password) ---"
  GO_BAD3=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GO_BASE$GO_PREFIX/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${USER_GO}_java\",\"password\":\"wrong\"}")
  JAVA_BAD=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$JAVA_BASE$JAVA_PREFIX/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USER_JAVA\",\"password\":\"wrong\"}")
  check_java "login_wrong_pw" "$GO_BAD3" "$JAVA_BAD" "no" "no"

  # 5. Get me (authenticated)
  echo "--- Auth Me ---"
  GO_ME3_S=$(curl -s -o /dev/null -w "%{http_code}" "$GO_BASE$GO_PREFIX/auth/me" \
    -H "Authorization: Bearer $GO_TOKEN3")
  JAVA_ME_S=$(curl -s -o /dev/null -w "%{http_code}" "$JAVA_BASE$JAVA_PREFIX/auth/me" \
    -H "Authorization: Bearer $JAVA_TOKEN")
  check_java "auth_me" "$GO_ME3_S" "$JAVA_ME_S" "yes" "yes"

  # 6. Get me (no token)
  echo "--- Auth Me (no token) ---"
  GO_NOAUTH3=$(curl -s -o /dev/null -w "%{http_code}" "$GO_BASE$GO_PREFIX/auth/me")
  JAVA_NOAUTH=$(curl -s -o /dev/null -w "%{http_code}" "$JAVA_BASE$JAVA_PREFIX/auth/me")
  check_java "auth_me_notoken" "$GO_NOAUTH3" "$JAVA_NOAUTH" "no" "no"

  # 7. Video list (public)
  echo "--- Video List ---"
  GO_VID3=$(curl -s -o /dev/null -w "%{http_code}" "$GO_BASE$GO_PREFIX/videos")
  JAVA_VID=$(curl -s -o /dev/null -w "%{http_code}" "$JAVA_BASE$JAVA_PREFIX/videos")
  check_java "video_list" "$GO_VID3" "$JAVA_VID" "yes" "yes"

  # 8. Duplicate register
  echo "--- Duplicate Register ---"
  GO_DUP3=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GO_BASE$GO_PREFIX/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${USER_GO}_java\",\"password\":\"Test1234!\"}")
  JAVA_DUP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$JAVA_BASE$JAVA_PREFIX/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USER_JAVA\",\"password\":\"Test1234!\"}")
  check_java "duplicate_register" "$GO_DUP3" "$JAVA_DUP" "no" "no"
  echo ""
fi

echo ""
echo "============================================"
echo "  RESULTS: $PASS passed, $FAIL failed"
echo "============================================"
