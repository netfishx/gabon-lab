-include .env
export

.PHONY: up down migrate migrate-down migrate-status seed \
        dev-go dev-rust build-go build-rust test-go test-rust \
        lint-go lint-rust clean

# ─── Infrastructure ─────────────────────────────

up:
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

# ─── Benchmarks ─────────────────────────────────

bench-oha:
	bash bench/oha-endpoints.sh

bench-k6-go:
	k6 run bench/k6-scenario.js --env BASE_URL=http://localhost:8080 --env PREFIX=/api/v1

bench-k6-rust:
	k6 run bench/k6-scenario.js --env BASE_URL=http://localhost:3000 --env PREFIX=/api

bench-metrics:
	bash bench/metrics.sh

bench-correctness:
	bash bench/correctness.sh

bench-all: bench-correctness bench-metrics bench-oha
