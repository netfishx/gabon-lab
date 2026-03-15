-include ../.env
export

.PHONY: dev build lint test check fmt docker-build docker-run

# Dev
dev:
	cargo run -p gabon-api

build:
	cargo build --release -p gabon-api

# Quality
check:
	cargo check --workspace

lint:
	cargo clippy --workspace -- -Dwarnings

fmt:
	cargo fmt --all -- --check

test:
	cargo test --workspace

# Database (sqlx-cli: cargo install sqlx-cli)
migrate:
	sqlx migrate run --source migrations --database-url "$$DATABASE_URL"

migrate-revert:
	sqlx migrate revert --source migrations --database-url "$$DATABASE_URL"

migrate-info:
	sqlx migrate info --source migrations --database-url "$$DATABASE_URL"

# Docker
docker-build:
	docker build -t gabon-rust .

docker-run:
	docker run --rm --env-file ../.env -p 3000:3000 gabon-rust
