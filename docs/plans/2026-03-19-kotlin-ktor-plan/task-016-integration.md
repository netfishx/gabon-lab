# Task 016: Integration and Deployment

**type**: setup
**depends-on**: [007-auth-impl, 008-video-impl, 009-like-impl, 010-social-impl, 011-profile-impl, 012-task-impl, 013-admin-impl, 014-report-impl, 015-ratelimit-impl]

## Description

Final integration task: Dockerize the Kotlin service, integrate into the top-level Makefile and bench scripts, and create project-specific documentation. This task makes the Kotlin implementation a first-class citizen alongside Go, Rust, and Java.

Key implementation areas:

- **Dockerfile** (multi-stage):
  - Stage 1 (build): `eclipse-temurin:21-jdk` base, copy Gradle wrapper + sources, run `./gradlew buildFatJar --no-daemon`. Use `--mount=type=cache,target=/root/.gradle` for Gradle cache.
  - Stage 2 (runtime): `eclipse-temurin:21-jre-alpine` base, copy fat JAR from build stage. JVM flags: `-XX:+UseZGC -XX:+ZGenerational -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/urandom`. Set timezone to `Asia/Shanghai`.
  - Expose port 8090. `ENTRYPOINT ["java", ...]`.

- **Makefile targets** (add to top-level Makefile):
  - `dev-kotlin`: `cd kotlin && ./gradlew run`
  - `build-kotlin`: `cd kotlin && ./gradlew buildFatJar`
  - `test-kotlin`: `cd kotlin && ./gradlew test`
  - `lint-kotlin`: `cd kotlin && ./gradlew detekt` (or ktlint, matching task-001 choice)
  - `bench-k6-kotlin`: k6 scenario against port 8090

- **.env.example**: add `KOTLIN_PORT=8090`

- **Bench script updates**:
  - `bench/correctness.sh`: add Kotlin endpoint (port 8090, prefix `/api/v1`)
  - `bench/metrics.sh`: add Kotlin metrics collection (binary size, startup time, memory)
  - `bench/oha-endpoints.sh`: add Kotlin endpoints for QPS testing
  - `bench/k6-scenario.js`: add Kotlin base URL option (port 8090)

- **kotlin/CLAUDE.md**: project-specific instructions for the Kotlin implementation:
  - Tech stack: Ktor 3.4.0 + Exposed + HikariCP + Lettuce + kotlinx-serialization
  - Package structure: `lab.gabon.{model,repository,service,route,plugin,config}`
  - Dev commands: `./gradlew run`, `./gradlew test`, `./gradlew buildFatJar`
  - Port 8090, API prefix `/api/v1/` and `/admin/v1/`
  - Testing patterns: testApplication, embedded test engine
  - Key design decisions (sealed error hierarchy, route-scoped plugins, etc.)

- **Architecture doc update**: update `docs/plans/2026-03-19-kotlin-ktor-design/architecture.md` if any implementation decisions deviated from the original design.

## Files

- `kotlin/Dockerfile` -- multi-stage Docker build (temurin:21-jdk build, temurin:21-jre-alpine runtime, ZGC flags)
- `kotlin/CLAUDE.md` -- project-specific Claude Code instructions for the Kotlin implementation
- `Makefile` -- add dev-kotlin, build-kotlin, test-kotlin, lint-kotlin, bench-k6-kotlin targets
- `.env.example` -- add KOTLIN_PORT=8090
- `bench/correctness.sh` -- add Kotlin (port 8090, /api/v1 prefix)
- `bench/metrics.sh` -- add Kotlin metrics (binary size, startup, memory)
- `bench/oha-endpoints.sh` -- add Kotlin endpoints
- `bench/k6-scenario.js` -- add Kotlin base URL prefix
- `docs/plans/2026-03-19-kotlin-ktor-design/architecture.md` -- update if needed

## Verification

1. `docker build -t gabon-kotlin kotlin/` succeeds and produces a runnable image
2. `docker run --rm -p 8090:8090 gabon-kotlin` starts and responds to `/health`
3. `make dev-kotlin` starts the development server on port 8090
4. `make test-kotlin` runs all tests successfully
5. `make bench-correctness` includes Kotlin and all API checks pass
6. `./gradlew test` passes all 11 BDD features (Features 1-11)
7. Docker image size is reasonable (< 300MB with JRE alpine)
