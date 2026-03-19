# Repository Guidelines

## Project Structure & Module Organization
`gabon-lab` compares four backend implementations of the same short-video platform. Use [go](/Users/ethanwang/projects/gabon-lab/go), [rust](/Users/ethanwang/projects/gabon-lab/rust), [java](/Users/ethanwang/projects/gabon-lab/java), and [kotlin](/Users/ethanwang/projects/gabon-lab/kotlin) for language-specific work; keep shared infrastructure in [docker-compose.yml](/Users/ethanwang/projects/gabon-lab/docker-compose.yml), benchmarks in [bench](/Users/ethanwang/projects/gabon-lab/bench), helper scripts in [scripts](/Users/ethanwang/projects/gabon-lab/scripts), and reports in [docs](/Users/ethanwang/projects/gabon-lab/docs). Go code lives in `cmd/api`, `internal/*`, `db/migrations`, and `tests/integration`. Rust is a workspace split into `crates/api`, `crates/domain`, `crates/infra`, and `crates/shared`, with SQLx migrations in `rust/migrations`. Java uses Gradle single-module with `lab.gabon` package (Spring Boot 4.0 + JDK 26). Kotlin uses Gradle with Ktor 3.4 + Exposed, with Flyway auto-migration on startup.

## Build, Test, and Development Commands
Start shared services with `make up`, apply PostgreSQL migrations with `make migrate`, and provision S3 buckets with `make init-storage`. Run servers with `make dev-go`, `make dev-rust`, `make dev-java`, and `make dev-kotlin`; build them with `make build-go`, `make build-rust`, `make build-java`, and `make build-kotlin`. Quality gates are `make test-go`, `cd go && make test-integration`, `make lint-go`, `make test-rust`, `make lint-rust`, `make test-java`, `make lint-java`, `make test-kotlin`, and `make lint-kotlin`. Use `make bench-all` only after services are healthy. Java is wired into the root Makefile: `make dev-java`, `make test-java`, `make lint-java`, `make migrate-java`.

## Coding Style & Naming Conventions
Follow the native toolchain. Go should stay `gofmt`/`goimports` clean and pass `golangci-lint`; keep handlers thin in `internal/transport` and core logic in `internal/service`. Do not hand-edit generated `go/internal/repository/*.sql.go`; update `go/db/queries/*.sql` and run `cd go && make sqlc`. Rust uses `cargo fmt`, `cargo clippy --workspace -- -Dwarnings`, snake_case modules, and the existing API/domain/infra split. Java follows the `lab.gabon.*` package layout with controller/service/repository separation (Spring Data JDBC). Kotlin follows standard Ktor project layout with routes/service/repository separation.

## Testing Guidelines
Place Go tests next to the package as `*_test.go`; put cross-service or database-backed cases in `go/tests/integration`. Rust tests live inline under `#[cfg(test)]`. Java tests belong under `src/test/java` using JUnit 5 + Testcontainers for integration tests. Kotlin tests belong under `src/test/kotlin`. There is no published coverage threshold, so require tests for every behavior change in service, repository, auth, or migration code.

## Commit & Pull Request Guidelines
Recent history uses Conventional Commits such as `fix(rust): ...` and `refactor(rust): ...`; keep that format and scope by implementation when possible. PRs should state which stack changed, list required env or schema updates, and include the commands you ran. If a PostgreSQL schema change affects Go, Rust, Java, or Kotlin, keep `go/db/migrations`, `rust/migrations`, and the respective Flyway migrations aligned in the same review.

## Security & Configuration Tips
Copy `.env.example` to `.env` for local work and never commit real `DATABASE_URL`, JWT secrets, or S3 credentials. Treat benchmark scripts as local tooling, not a substitute for tests.

## Agent-Specific Instructions
Before exploring, editing, or running repository workflows, load and follow every applicable rule under `~/.claude/rules`. Treat those files as higher-priority operating guidance for repository work, alongside this document and any local module-specific instructions such as `go/CLAUDE.md` or `rust/CLAUDE.md`.
