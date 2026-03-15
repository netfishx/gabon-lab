FROM azul/zulu-openjdk-debian:17 AS builder
WORKDIR /build
RUN apt-get update && apt-get install -y --no-install-recommends maven && rm -rf /var/lib/apt/lists/*
COPY pom.xml .
COPY gabon-common/ gabon-common/
COPY gabon-service/ gabon-service/
COPY gabon-admin/pom.xml gabon-admin/pom.xml
RUN mkdir -p gabon-admin/src/main/java
RUN mvn package -DskipTests -pl gabon-service -am -q

FROM azul/zulu-openjdk-debian:17-jre
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
COPY --from=builder /build/gabon-service/target/gabon-service.jar /app.jar
USER appuser
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app.jar"]
