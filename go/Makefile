-include ../.env
export

.PHONY: dev build lint test test-integration sqlc docker-build docker-run

# Dev
dev:
	go run cmd/api/main.go

build:
	go build -o bin/api cmd/api/main.go

# Quality
lint:
	golangci-lint run ./...

test:
	go test -v -race -count=1 ./...

test-integration:
	go test -v -race -count=1 -tags=integration -timeout 5m ./tests/integration/...

# Database
migrate:
	goose -dir db/migrations postgres "$$DATABASE_URL" up

migrate-down:
	goose -dir db/migrations postgres "$$DATABASE_URL" down

migrate-status:
	goose -dir db/migrations postgres "$$DATABASE_URL" status

# Code generation
sqlc:
	sqlc generate

# Docker
docker-build:
	docker build -t gabon-go .

docker-run:
	docker run --rm --env-file ../.env -p 8080:8080 gabon-go
