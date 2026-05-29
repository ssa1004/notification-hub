# notification-hub — 자주 쓰는 명령 단일 진입점
#
#   make up        인프라(Postgres/Redis/Kafka/Kafka-UI) 기동
#   make ps        컨테이너 상태
#   make logs      인프라 로그 follow
#   make run       앱 호스트 실행 (:8080, H2 + Mock vendor)
#   make run-prod  앱 호스트 실행 (prod 프로파일 — 위 인프라에 연결)
#   make demo      cross-repo 통합 데모 (integration compose 위에서 한 사이클)
#   make down      인프라 정지 (볼륨 유지)
#   make clean     인프라 정지 + 볼륨 삭제 (옛 데이터 제거)
#   make build     전체 gradle 빌드 (테스트 제외)
#   make test      전체 테스트 (./gradlew check)
#
# 앱은 호스트에서 ./gradlew :notification-bootstrap:bootRun 으로 띄운다 — Kafka 는
# localhost:9092 로 붙는다 (infrastructure/docker-compose.yml 의 EXTERNAL listener).
# 자세한 건 README "실행 방법".

COMPOSE     := docker compose -f infrastructure/docker-compose.yml
COMPOSE_INT := docker compose -f infrastructure/docker-compose.integration.yml
GRADLE      := ./gradlew
APP         := notification-bootstrap

.DEFAULT_GOAL := help
.PHONY: help up ps logs run run-prod demo down clean build test urls

help: ## 이 도움말
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
	  | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

up: ## 인프라 기동 (Postgres/Redis/Kafka/Kafka-UI)
	$(COMPOSE) up -d postgres redis kafka kafka-ui
	@echo "→ Kafka UI http://localhost:8081 · Postgres :5432 · Redis :6379 · Kafka :9092"

ps: ## 컨테이너 상태
	$(COMPOSE) ps

logs: ## 인프라 로그 follow
	$(COMPOSE) logs -f --tail=100

run: ## 앱 호스트 실행 (:8080, H2 + Mock vendor, 외부 의존 0)
	$(GRADLE) :$(APP):bootRun

run-prod: ## 앱 호스트 실행 (prod 프로파일 — make up 으로 띄운 인프라에 연결)
	SPRING_PROFILES_ACTIVE=prod $(GRADLE) :$(APP):bootRun

demo: ## cross-repo 통합 데모 (integration compose 를 띄운 뒤 한 사이클)
	$(COMPOSE_INT) up -d
	./scripts/integration-demo.sh

down: ## 인프라 정지 (볼륨 유지)
	$(COMPOSE) down

clean: ## 인프라 정지 + 볼륨 삭제 (다음 기동 시 깨끗한 상태)
	$(COMPOSE) down -v

build: ## 전체 gradle 빌드 (테스트 제외)
	$(GRADLE) build -x test

test: ## 전체 테스트 (단위 + Testcontainers)
	$(GRADLE) check

urls: ## 주요 UI / 엔드포인트
	@echo "Swagger     http://localhost:8080/swagger"
	@echo "Prometheus  http://localhost:8080/actuator/prometheus"
	@echo "Kafka UI    http://localhost:8081"
	@echo "app :8080 · Postgres :5432 · Redis :6379 · Kafka :9092"
