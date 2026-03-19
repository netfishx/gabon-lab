# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/libs.versions.toml gradle/libs.versions.toml

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies

COPY src/ src/

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew shadowJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache tzdata curl \
    && addgroup -S app && adduser -S app -G app
ENV TZ=Asia/Shanghai
COPY --from=builder /build/build/libs/gabon-api.jar /app/gabon-api.jar
USER app
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:8090/health || exit 1
ENTRYPOINT ["java", \
    "-XX:+UseZGC", "-XX:+ZGenerational", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/urandom", \
    "-jar", "/app/gabon-api.jar"]
