# Dockerize gabon-go

## Context

Go API backend connecting to hosted Supabase PG + Upstash Redis.
Deploy as Docker image to VPS. No local DB/Redis containers needed.

## Deliverables

3 files: Dockerfile, .dockerignore, Makefile updates.

## Dockerfile

Multi-stage build:

```
Stage 1 — builder (golang:1.26-alpine)
  WORKDIR /build
  COPY go.mod go.sum → go mod download (layer cache)
  COPY . .
  CGO_ENABLED=0 GOOS=linux go build -o /api cmd/api/main.go

Stage 2 — runtime (alpine:3.21)
  ca-certificates (TLS for Supabase/Upstash)
  Non-root user (appuser:appgroup)
  COPY --from=builder /api /api
  EXPOSE 8080
  ENTRYPOINT ["/api"]
```

Final image ~15MB. Static binary, no libc dependency.

## .dockerignore

```
.git
.env
.env.*
.claude/
docs/
tests/
bin/
*.test
README.md
CLAUDE.md
```

## Makefile

Add two targets:

```makefile
docker-build:
	docker build -t gabon-api .

docker-run:
	docker run --env-file .env -p 8080:8080 gabon-api
```

## Non-goals

- docker-compose (PG/Redis are hosted services)
- CI image push (separate concern, add later)
- Health check in Dockerfile (app already has /health)
