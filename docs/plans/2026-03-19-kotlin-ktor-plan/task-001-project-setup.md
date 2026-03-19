# Task 001: Project scaffold and Gradle configuration

**type**: setup
**depends-on**: []

## Description

Initialize the Kotlin/Ktor project under `kotlin/` directory with Gradle Kotlin DSL. Set up the entire build system and a minimal running application.

Key decisions:

- **Version Catalog** (`gradle/libs.versions.toml`): centralize ALL dependency versions — Kotlin 2.3.20, Ktor 3.4.0, Exposed 1.1.1, HikariCP 6.3.0, kotlinx-serialization 1.10.0, Lettuce 7.3.0, AWS SDK for Kotlin 1.5.5, bcrypt 0.10.2, Logback. Define version variables, libraries, and bundles.
- **build.gradle.kts**: apply `kotlin("jvm")`, `kotlin("plugin.serialization")`, `io.ktor.plugin` (fat jar). Set JVM toolchain to 21. Configure `application { mainClass }` pointing to `lab.gabon.ApplicationKt`. Enable K2 compiler. Configure JVM args for ZGC (`-XX:+UseZGC`).
- **gradle.properties**: set `kotlin.code.style=official`, org.gradle.jvmargs for build process, enable configuration cache if compatible.
- **settings.gradle.kts**: project name `gabon-kotlin`, enable version catalog.
- **Application.kt**: minimal `fun main()` using `embeddedServer(Netty, port = 8090)` with a single GET `/health` route returning `JsonData.ok("ok")` (can use a temporary inline data class until Task 002 formalizes it). Package: `lab.gabon`.
- **logback.xml**: structured logging config with console appender, pattern including timestamp/level/logger/message. Set root level to INFO, Ktor internals to WARN.
- **Makefile**: targets `dev`, `build`, `test`, `lint`, `clean`. Each target should source `../.env` before running Gradle commands. `dev` runs `./gradlew run`, `build` runs `./gradlew build`, `test` runs `./gradlew test`, `lint` runs `./gradlew detekt` or ktlint (pick one).
- **.gitignore**: standard Kotlin/Gradle ignores — `.gradle/`, `build/`, `.idea/`, `*.iml`, `local.properties`.

## Files

- `kotlin/build.gradle.kts` — Gradle build script with all plugins, dependencies, JVM 21 toolchain, fat jar config
- `kotlin/settings.gradle.kts` — project name and version catalog enablement
- `kotlin/gradle.properties` — Kotlin/Gradle build properties
- `kotlin/gradle/libs.versions.toml` — version catalog with all dependency coordinates
- `kotlin/src/main/kotlin/lab/gabon/Application.kt` — minimal main function with embeddedServer, /health endpoint
- `kotlin/src/main/resources/logback.xml` — logging configuration
- `kotlin/Makefile` — dev/build/test/lint targets loading ../.env
- `kotlin/.gitignore` — Gradle/Kotlin ignore patterns

## Verification

1. `cd kotlin && ./gradlew build` completes without errors
2. `./gradlew run` starts the server on port 8090 (visible in logs)
3. `curl localhost:8090/health` returns `{"code":0,"message":"ok","data":"ok"}`
4. `./gradlew jar` or `./gradlew buildFatJar` produces a runnable fat JAR
5. Verify JVM 21 toolchain is active: `./gradlew --version` shows JDK 21+
