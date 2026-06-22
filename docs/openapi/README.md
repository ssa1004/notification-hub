# OpenAPI spec

`notification-hub` 의 REST API 를 OpenAPI 3 spec 으로 build-time export 한다.

> **현재 상태**: `notification-hub.yaml` 이 커밋되어 있다. 기본(default) 프로파일은 H2 인메모리 +
> mock vendor 로 동작하고 Redis / Kafka health indicator 가 꺼져 있어, **외부 인프라 없이** 앱이
> 부팅된다. 따라서 spec 은 Postgres / Redis / Kafka 없이 생성·갱신할 수 있다. 손으로 작성하지
> 않는다 (실제 라우팅 / 스키마와 어긋날 수 있으므로).

## 무엇이 들어가나

- `notification-hub.yaml` — 빌드 시 생성되는 OpenAPI 3.1 문서. 외부 참조 / SDK codegen 의 단일 진실값.
  - 알림 발송 / 조회 (`/api/v1/notifications`, `/api/v1/notifications/me`, `/api/v1/notifications/{id}`)
  - 디바이스 토큰 / 사용자 채널 선호 (`/api/v1/devices`, `/api/v1/users/{recipientId}/preferences`)
  - 템플릿 (`/api/v1/templates`)
  - 전달 ack 콜백 / DLQ 운영 (`/api/v1/deliveries/{id}/ack`, `/api/v1/admin/dlq/**`)

> 이 디렉토리의 `*.yaml` 은 앱을 부팅해 생성·갱신한다. 로컬에서 수기로 편집하지 않는다.
> CI 의 spec drift gate (`.github/workflows/ci.yml` 의 `openapi-spec` job) 가 spec 을 재생성해
> `git diff --exit-code` 로 표류(drift)를 막는다 — 코드와 커밋된 spec 이 어긋나면 CI 가 실패한다.

## 생성 방법

`info.title` / `version` / `servers` 는 `OpenApiConfig` (adapter-in) 에서 고정해 부팅 포트와
무관하게 **결정론적(deterministic)** 산출물을 만든다 (drift gate 가 포트 차이로 헛되이 실패하지
않도록).

### 1) 외부 인프라 없이 부팅 후 fetch (권장)

```bash
# 빈 포트로 앱을 띄우고 (기본 프로파일 = H2 + mock vendor),
./gradlew :notification-bootstrap:bootRun --args='--server.port=8080'
# 다른 셸에서 spec 을 받아 저장
curl -s http://localhost:8080/v3/api-docs.yaml -o docs/openapi/notification-hub.yaml
```

### 2) springdoc-gradle-plugin 의 build-time export

```bash
./gradlew :notification-bootstrap:generateOpenApiDocs
```

`org.springdoc.openapi-gradle-plugin` 을 `notification-bootstrap` 모듈에 적용했다.
`generateOpenApiDocs` 가 앱을 부팅한 뒤 `/v3/api-docs.yaml` 을 받아
`docs/openapi/notification-hub.yaml` 로 저장한다. 기본 프로파일이라 외부 인프라가 필요 없다.

## 보는 법

- Swagger UI — 앱 실행 후 `http://localhost:8080/swagger`
- Redoc — `npx @redocly/cli preview-docs docs/openapi/notification-hub.yaml`
- 통합 뷰어 — profile repo `ssa1004/ssa1004` 의 `docs/api/index.html` (11 service spec 드롭다운)
