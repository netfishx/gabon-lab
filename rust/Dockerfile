# syntax=docker/dockerfile:1
FROM rust:nightly-slim AS chef
RUN cargo install cargo-chef --locked
WORKDIR /build

FROM chef AS planner
COPY Cargo.toml Cargo.lock ./
COPY crates/api/Cargo.toml crates/api/Cargo.toml
COPY crates/domain/Cargo.toml crates/domain/Cargo.toml
COPY crates/infra/Cargo.toml crates/infra/Cargo.toml
COPY crates/shared/Cargo.toml crates/shared/Cargo.toml
RUN mkdir -p crates/api/src && echo "fn main() {}" > crates/api/src/main.rs && \
    mkdir -p crates/domain/src && touch crates/domain/src/lib.rs && \
    mkdir -p crates/infra/src && touch crates/infra/src/lib.rs && \
    mkdir -p crates/shared/src && touch crates/shared/src/lib.rs
RUN cargo chef prepare --recipe-path recipe.json

FROM chef AS builder
COPY --from=planner /build/recipe.json recipe.json
RUN --mount=type=cache,target=/build/target \
    --mount=type=cache,target=/usr/local/cargo/registry \
    --mount=type=cache,target=/usr/local/cargo/git \
    cargo chef cook --release --recipe-path recipe.json
COPY Cargo.toml Cargo.lock ./
COPY crates/ crates/
RUN --mount=type=cache,target=/build/target \
    --mount=type=cache,target=/usr/local/cargo/registry \
    --mount=type=cache,target=/usr/local/cargo/git \
    cargo build --release -p gabon-api && \
    cp target/release/gabon-api /usr/local/bin/gabon-api

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl && rm -rf /var/lib/apt/lists/* \
    && groupadd -r appgroup && useradd -r -g appgroup appuser
COPY --from=builder /usr/local/bin/gabon-api /gabon-api
USER appuser
ENV PORT=3000
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:3000/health || exit 1
ENTRYPOINT ["/gabon-api"]
