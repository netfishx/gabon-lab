#!/bin/bash
# Engineering metrics comparison: Go vs Rust
set -e

echo "============================================"
echo "  ENGINEERING METRICS: Go vs Rust"
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

# 2. Build time
echo "--- Build Time (clean) ---"
echo "[Go]"
(cd go && go clean -cache 2>/dev/null; time go build ./... 2>&1) 2>&1
echo ""
echo "[Rust]"
(cd rust && cargo clean 2>/dev/null; time cargo build --release -p gabon-api 2>&1 | tail -1) 2>&1
echo ""

# 3. Binary size
echo "--- Binary Size ---"
(cd go && go build -o bin/api cmd/api/main.go 2>/dev/null)
echo "[Go]  $(du -h go/bin/api | cut -f1)"
echo "[Rust] $(du -h rust/target/release/gabon-api 2>/dev/null | cut -f1 || echo 'not built')"
echo ""

# 4. Docker image size (if built)
echo "--- Docker Image Size ---"
docker images --format "{{.Repository}}\t{{.Size}}" 2>/dev/null | grep -E "gabon" || echo "(not built — run 'make build-go && make build-rust' with Docker)"
echo ""

# 5. Dependency count
echo "--- Dependency Count ---"
echo "[Go]  $(cd go && go list -m all 2>/dev/null | wc -l | tr -d ' ') modules"
echo "[Rust] $(cd rust && cargo metadata --format-version 1 2>/dev/null | jq '.packages | length') crates"
echo ""

# 6. Test count
echo "--- Test Count ---"
echo "[Go]  $(cd go && go test ./... -v 2>&1 | grep -c '--- PASS\|--- FAIL') tests"
echo "[Rust] $(cd rust && cargo test --workspace 2>&1 | grep -oE '[0-9]+ passed' | head -1 || echo 'unknown')"
echo ""

echo "============================================"
echo "  METRICS COMPLETE"
echo "============================================"
