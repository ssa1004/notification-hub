# OpenAPI spec

`notification-hub` 의 REST API 를 OpenAPI 3 spec 으로 build-time export 한다.

## 무엇이 들어가나

- `notification-hub.yaml` — 빌드 시 생성되는 OpenAPI 3 문서. 외부 참조 / SDK codegen 의 단일 진실값.
  - 알림 발송 (`/api/v1/notifications`)
  - 디바이스 토큰 / 사용자 채널 선호 (`/api/v1/device-tokens`, `/api/v1/users/.../preferences`)
  - 템플릿 (`/api/v1/templates`)
  - 전달 ack 콜백 / DLQ 운영 (`/api/v1/deliveries/.../ack`, `/api/v1/admin/dlq`)

> 이 디렉토리의 `*.yaml` 은 CI 에서 생성·갱신된다. 로컬에서 수기로 편집하지 않는다.

## 생성 방법

`org.springdoc.openapi-gradle-plugin` 을 `notification-bootstrap` 모듈에 적용했다.
`generateOpenApiDocs` 태스크가 앱을 부팅한 뒤 `/v3/api-docs.yaml` 을 받아
`docs/openapi/notification-hub.yaml` 로 저장한다.

```bash
./gradlew :notification-bootstrap:generateOpenApiDocs
```

앱 부팅에 Postgres / Redis / Kafka 가 필요하므로, 의존 인프라를 먼저 띄워야 한다.
CI 에서는 service container 를 띄운 잡에서 위 태스크를 실행해 산출된 yaml 을
commit 하거나 아티팩트로 업로드한다.

## 보는 법

- Swagger UI — 앱 실행 후 `http://localhost:8080/swagger`
- Redoc — `npx @redocly/cli preview-docs docs/openapi/notification-hub.yaml`
- 통합 뷰어 — profile repo `ssa1004/ssa1004` 의 `docs/api/index.html` (10 service spec 드롭다운)
