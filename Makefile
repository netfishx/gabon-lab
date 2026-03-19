-include .env
export DATABASE_URL REDIS_URL GO_PORT RUST_PORT KOTLIN_PORT

.PHONY: up down migrate migrate-down migrate-status seed \
        dev-go dev-rust dev-kotlin build-go build-rust build-kotlin \
        test-go test-rust test-kotlin lint-go lint-rust lint-kotlin clean

# ─── Infrastructure ─────────────────────────────

up:
	@test -f garage.toml || cp garage.toml.example garage.toml
	docker compose up -d
	@echo "Waiting for postgres..."
	@until docker compose exec -T postgres pg_isready -U postgres > /dev/null 2>&1; do sleep 1; done
	@echo "Postgres ready. Run 'make migrate && make init-storage' to set up."

init-storage:
	bash scripts/init-garage.sh

down:
	docker compose down

clean:
	docker compose down -v

# ─── Database (each manages its own migrations) ─
# Note: Kotlin uses Flyway auto-migration on startup, no manual step needed.

migrate-go:
	cd go && make migrate

migrate-rust:
	cd rust && make migrate

migrate: migrate-go migrate-rust

# ─── Go ─────────────────────────────────────────

dev-go:
	cd go && PORT=$$GO_PORT make dev

build-go:
	cd go && make build

test-go:
	cd go && make test

lint-go:
	cd go && make lint

# ─── Rust ───────────────────────────────────────

dev-rust:
	cd rust && PORT=$$RUST_PORT make dev

build-rust:
	cd rust && make build

test-rust:
	cd rust && make test

lint-rust:
	cd rust && make lint

# ─── Kotlin ─────────────────────────────────────

dev-kotlin:
	cd kotlin && ./gradlew run --no-daemon

build-kotlin:
	cd kotlin && ./gradlew shadowJar --no-daemon

test-kotlin:
	cd kotlin && ./gradlew test --no-daemon

lint-kotlin:
	cd kotlin && ./gradlew build --no-daemon

# ─── Benchmarks ─────────────────────────────────

bench-oha:
	bash bench/oha-endpoints.sh

bench-k6-go:
	k6 run bench/k6-scenario.js --env BASE_URL=http://localhost:8080 --env PREFIX=/api/v1

bench-k6-rust:
	k6 run bench/k6-scenario.js --env BASE_URL=http://localhost:3000 --env PREFIX=/api

bench-k6-kotlin:
	k6 run bench/k6-scenario.js --env BASE_URL=http://localhost:8090 --env PREFIX=/api/v1

bench-metrics:
	bash bench/metrics.sh

bench-correctness:
	bash bench/correctness.sh

bench-all: bench-correctness bench-metrics bench-oha
