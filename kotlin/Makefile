-include ../.env
export

.PHONY: dev build test lint format clean

dev:
	./gradlew run --no-daemon

build:
	./gradlew shadowJar --no-daemon

test:
	./gradlew test --no-daemon

lint:
	./gradlew ktlintCheck detekt --no-daemon

format:
	./gradlew ktlintFormat --no-daemon

clean:
	./gradlew clean --no-daemon
