# gabon-lab

Short video platform backend — four implementations (Java / Go / Rust / Kotlin) for language comparison benchmarking.

## Why

> "该换语言就换语言" — Pick the right language for the job, not the one you're used to.

This lab answers the question: **what do you actually gain (and lose) by switching from Java to Go or Rust for backend services?**

## Results at a Glance

| | Java (Spring Boot) | Go (Echo) | Rust (Axum) | Kotlin (Ktor) |
|---|---|---|---|---|
| **Throughput** | *(re-benchmark pending)* | 96K req/s | **182K req/s** | *(pending)* |
| **Memory** | *(re-benchmark pending)* | 43 MB | **19 MB** | *(pending)* |
| **Cold start** | *(re-benchmark pending)* | **33 ms** | 135 ms | *(pending)* |
| **Compile** | *(re-benchmark pending)* | 10.3s | 97.8s | *(pending)* |
| **Code** | *(re-benchmark pending)* | 7,087 LOC | **4,179 LOC** | *(pending)* |
| **Docker image** | *(re-benchmark pending)* | 150 MB | **130 MB** | *(pending)* |

Full report: [docs/benchmark-report.md](docs/benchmark-report.md)

## Quick Start

```bash
# Prerequisites: Docker, Go 1.26+, Rust 1.94+, JDK 26+, goose, k6, oha
cp .env.example .env
make up          # Start PostgreSQL 18 + Redis 8 + Garage S3
make migrate     # Apply database migrations
make init-storage # Create S3 buckets

# Run servers
make dev-go      # :8080
make dev-rust    # :3000
make dev-java    # :8082
make dev-kotlin  # :8090

# Benchmark
make bench-all
```

## Architecture

```
gabon-lab/
├── java/              Java (Spring Boot 4.0 + Spring Data JDBC + PostgreSQL)
├── go/                Go (Echo + pgx + sqlc + PostgreSQL)
│   └── db/migrations/   goose migrations
├── rust/              Rust (Axum + SQLx + Tokio + PostgreSQL)
│   └── migrations/      sqlx migrations
├── kotlin/            Kotlin (Ktor + Exposed + Flyway + PostgreSQL)
├── bench/             Benchmark scripts (oha + k6)
└── docker-compose.yml
```

All four share the same Docker infrastructure (PG, Redis, Garage S3). Each manages PostgreSQL migrations independently (Go: goose, Rust: sqlx, Java: Flyway via Gradle, Kotlin: Flyway auto). All use a unified int code response envelope.

## Tech Stack

| | Java | Go | Rust | Kotlin |
|---|---|---|---|---|
| HTTP | Spring Boot 4.0 | Echo v4 | Axum 0.8 | Ktor 3.4 |
| Database | Spring Data JDBC / PG | pgx/v5 + sqlc / PG | SQLx / PG | Exposed / PG |
| Cache | Spring Data Redis | go-redis/v9 | deadpool-redis | Lettuce 7.5 |
| Storage | AWS SDK v2 | AWS SDK v2 | aws-sdk-s3 | AWS SDK Kotlin |
| Auth | java-jwt | golang-jwt/v5 | jsonwebtoken | java-jwt |
| Tests | JUnit 5 + Testcontainers | testify | #[test] + trait mock | JUnit 5 |

## Key Findings

**Rust wins runtime** — 2x throughput, 1/18 memory vs Java. Stays flat at 19 MB regardless of load.

**Go wins developer velocity** — 33 ms cold start, 10s compile, 1-week onboarding. Best choice for K8s/serverless.

**Java** — new Spring Boot 4.0 + PostgreSQL implementation pending re-benchmark. Old baseline (Maven + MySQL) numbers no longer apply.

**Kotlin** — Ktor + coroutines implementation pending initial benchmark.

**No overall winner** — only the best fit for your constraints.

## License

MIT
