-include ../.env
export

.PHONY: dev build test lint clean

dev:
	./gradlew run --no-daemon

build:
	./gradlew buildFatJar --no-daemon

test:
	./gradlew test --no-daemon

lint:
	./gradlew build --no-daemon

clean:
	./gradlew clean --no-daemon
