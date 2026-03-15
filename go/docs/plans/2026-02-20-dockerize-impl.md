# Dockerize Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Package gabon-go as a minimal Docker image for VPS deployment.

**Architecture:** Multi-stage build — golang:1.26-alpine compiles a static binary, alpine:3.21 runs it as non-root. App reads all config from env vars, connects to hosted Supabase PG + Upstash Redis.

**Tech Stack:** Docker, Alpine Linux, Make

---

### Task 1: Create .dockerignore

**Files:**
- Create: `.dockerignore`

**Step 1: Write .dockerignore**

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

**Step 2: Commit**

```bash
git add .dockerignore
git commit -m "chore: add .dockerignore"
```

---

### Task 2: Create Dockerfile

**Files:**
- Create: `Dockerfile`

**Step 1: Write Dockerfile**

```dockerfile
FROM golang:1.26-alpine AS builder
WORKDIR /build
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o /api cmd/api/main.go

FROM alpine:3.21
RUN apk add --no-cache ca-certificates \
    && addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /api /api
USER appuser
EXPOSE 8080
ENTRYPOINT ["/api"]
```

**Step 2: Build and verify**

Run: `docker build -t gabon-api .`
Expected: successful build, image ~15MB

Run: `docker images gabon-api --format '{{.Size}}'`
Expected: < 30MB

**Step 3: Test run (will fail without env vars, but should start)**

Run: `docker run --rm gabon-api 2>&1 | head -5`
Expected: error about missing DATABASE_URL or similar config — confirms binary runs

**Step 4: Commit**

```bash
git add Dockerfile
git commit -m "build: add multi-stage dockerfile"
```

---

### Task 3: Add Makefile docker targets

**Files:**
- Modify: `Makefile`

**Step 1: Add docker targets**

Append to Makefile after `sqlc` target:

```makefile
# Docker
docker-build:
	docker build -t gabon-api .

docker-run:
	docker run --env-file .env -p 8080:8080 gabon-api
```

Update `.PHONY` line to include `docker-build docker-run`.

**Step 2: Verify**

Run: `make docker-build`
Expected: successful build (uses cache from Task 2)

**Step 3: Commit**

```bash
git add Makefile
git commit -m "build: add docker-build and docker-run make targets"
```
