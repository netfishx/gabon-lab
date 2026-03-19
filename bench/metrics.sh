#!/bin/bash
# Engineering metrics comparison: Go vs Rust vs Kotlin vs Java
set -e

echo "============================================"
echo "  ENGINEERING METRICS: Go vs Rust vs Kotlin vs Java"
echo "============================================"
echo ""

cd "$(dirname "$0")/.."

# 1. Lines of code
echo "--- Lines of Code ---"
echo "[Go]"
tokei go/ --sort code 2>/dev/null | tail -5
echo ""
echo "[Rust]"
tokei rust/ --sort code 2>/dev/null | tail -5
echo ""
echo "[Kotlin]"
tokei kotlin/src/ --sort code 2>/dev/null | tail -5
echo ""
echo "[Java]"
tokei java/src/ --sort code 2>/dev/null | tail -5
echo ""

# 2. Build time
echo "--- Build Time (clean) ---"
echo "[Go]"
(cd go && go clean -cache 2>/dev/null; time go build ./... 2>&1) 2>&1
echo ""
echo "[Rust]"
(cd rust && cargo clean 2>/dev/null; time cargo build --release -p gabon-api 2>&1 | tail -1) 2>&1
echo ""
echo "[Kotlin]"
(cd kotlin && ./gradlew clean --no-daemon 2>/dev/null; time ./gradlew shadowJar --no-daemon 2>&1 | tail -1) 2>&1
echo ""
echo "[Java]"
(cd java && ./gradlew clean --no-daemon 2>/dev/null; time ./gradlew build -x test --no-daemon 2>&1 | tail -1) 2>&1
echo ""

# 3. Binary size
echo "--- Binary / JAR Size ---"
(cd go && go build -o bin/api cmd/api/main.go 2>/dev/null)
echo "[Go]    $(du -h go/bin/api | cut -f1)"
echo "[Rust]  $(du -h rust/target/release/gabon-api 2>/dev/null | cut -f1 || echo 'not built')"
echo "[Kotlin] $(du -h kotlin/build/libs/gabon-api.jar 2>/dev/null | cut -f1 || echo 'not built')"
echo "[Java]   $(du -h java/build/libs/*.jar 2>/dev/null | tail -1 | cut -f1 || echo 'not built')"
echo ""

# 4. Docker image size (if built)
echo "--- Docker Image Size ---"
docker images --format "{{.Repository}}\t{{.Size}}" 2>/dev/null | grep -E "gabon" || echo "(not built — run docker build for each)"
echo ""

# 5. Dependency count
echo "--- Dependency Count ---"
echo "[Go]    $(cd go && go list -m all 2>/dev/null | wc -l | tr -d ' ') modules"
echo "[Rust]  $(cd rust && cargo metadata --format-version 1 2>/dev/null | jq '.packages | length') crates"
echo "[Kotlin] $(cd kotlin && ./gradlew dependencies --configuration runtimeClasspath --no-daemon 2>/dev/null | grep -c '--- ' || echo 'unknown') libraries"
echo "[Java]   $(cd java && ./gradlew dependencies --configuration runtimeClasspath --no-daemon 2>/dev/null | grep -c '--- ' || echo 'unknown') libraries"
echo ""

# 6. Test count
echo "--- Test Count ---"
echo "[Go]    $(cd go && go test ./... -v 2>&1 | grep -c '--- PASS\|--- FAIL') tests"
echo "[Rust]  $(cd rust && cargo test --workspace 2>&1 | grep -oE '[0-9]+ passed' | head -1 || echo 'unknown')"
echo "[Kotlin] $(cd kotlin && ./gradlew test --no-daemon 2>&1 | grep -oE '[0-9]+ tests' | head -1 || echo 'unknown')"
echo "[Java]   $(cd java && ./gradlew test --no-daemon 2>&1 | grep -oE '[0-9]+ tests' | head -1 || echo 'unknown')"
echo ""

echo "============================================"
echo "  METRICS COMPLETE"
echo "============================================"
