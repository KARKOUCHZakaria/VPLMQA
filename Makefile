.PHONY: up down build-all test-all logs ps init-topics

## Start all services (dev mode with hot-reload volumes)
up:
	docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d

## Stop and remove all containers
down:
	docker compose down -v

## Build all Docker images in parallel
build-all:
	docker compose build --parallel

## Run all Maven test suites
test-all:
	for service in auth-service project-service member-service design-service \
	               e2e-service ticket-service notification-service analytics-service; do \
	  echo "Testing $$service..."; \
	  cd $$service && mvn test -q && cd ..; \
	done

## Tail logs for a specific service (usage: make logs s=auth-service)
logs:
	docker compose logs -f $(s)

## List running containers with status
ps:
	docker compose ps

## Initialize Kafka topics (run once after first up)
init-topics:
	docker compose exec kafka bash /topics-init.sh
