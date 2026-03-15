# gabon-lab

Short video platform backend — three implementations (Java / Go / Rust) for language comparison benchmarking.

## Why

> "该换语言就换语言" — Pick the right language for the job, not the one you're used to.

This lab answers the question: **what do you actually gain (and lose) by switching from Java to Go or Rust for backend services?**

## Results at a Glance

| | Java (Spring Boot) | Go (Echo) | Rust (Axum) |
|---|---|---|---|
| **Throughput** | 88K req/s | 96K req/s | **182K req/s** |
| **Memory** | 354 MB | 43 MB | **19 MB** |
| **Cold start** | 2,714 ms | **33 ms** | 135 ms |
| **Compile** | **5.6s** | 10.3s | 97.8s |
| **Code** | 7,001 LOC | 7,087 LOC | **4,179 LOC** |
| **Docker image** | 412 MB | 150 MB | **130 MB** |

Full report: [docs/benchmark-report.md](docs/benchmark-report.md)

## Quick Start

```bash
# Prerequisites: Docker, Go 1.26+, Rust 1.94+, goose, k6, oha
cp .env.example .env
make up          # Start PostgreSQL 18 + Redis 8 + MySQL 8.4 + Garage S3
make migrate     # Apply database migrations
make init-storage # Create S3 buckets

# Run both servers
make dev-go      # :8080
make dev-rust    # :3000

# Benchmark
make bench-all
```

## Architecture

```
gabon-lab/
├── java/              Java (Spring Boot + MyBatis-Plus + MySQL)
├── go/                Go (Echo + pgx + sqlc + PostgreSQL)
│   └── db/migrations/   goose migrations
├── rust/              Rust (Axum + SQLx + Tokio + PostgreSQL)
│   └── migrations/      sqlx migrations
├── bench/             Benchmark scripts (oha + k6)
└── docker-compose.yml
```

All three share the same Docker infrastructure (PG, MySQL, Redis, Garage S3). Go and Rust both use PostgreSQL but manage migrations independently (Go: goose, Rust: sqlx). Java uses MySQL. API designs are intentionally independent.

## Tech Stack

| | Java | Go | Rust |
|---|---|---|---|
| HTTP | Spring Boot 3.2 | Echo v4 | Axum 0.8 |
| Database | MyBatis-Plus / MySQL | pgx/v5 + sqlc / PG | SQLx / PG |
| Cache | Jedis + Redisson | go-redis/v9 | deadpool-redis |
| Storage | AWS S3 | AWS SDK v2 | aws-sdk-s3 |
| Auth | jjwt | golang-jwt/v5 | jsonwebtoken |
| Tests | JUnit | testify | #[test] + trait mock |

## Key Findings

**Rust wins runtime** — 2x throughput, 1/18 memory vs Java. Stays flat at 19 MB regardless of load.

**Go wins developer velocity** — 33 ms cold start, 10s compile, 1-week onboarding. Best choice for K8s/serverless.

**Java wins compilation speed** — javac generates bytecode without native linking, finishing in 5.6s. JVM startup cost (2.7s) and memory (354 MB) are the tradeoffs.

**No overall winner** — only the best fit for your constraints.

## License

MIT
