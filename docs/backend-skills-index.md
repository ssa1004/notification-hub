# 백엔드 스킬 인덱스 — 이 레포에서 무엇을 배우나

> 이 레포가 시연하는 백엔드 / 운영 패턴을 **"무엇 → 이 레포 어디서 → 왜(ADR) → 더 깊은 이론"** 으로 잇는 학습용 인덱스.
> "이 패턴 공부하려면 어디부터 보나"의 진입점. 설명을 다시 쓰지 않고 코드·결정·이론으로 연결만 한다.

## 메시징 · 일관성

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **Outbox 패턴** | [`adapter-out/outbox/OutboxRelay.kt`](../notification-adapter-out/src/main/kotlin/com/example/notification/adapter/out/outbox/OutboxRelay.kt) + `outbox_event` 테이블 | [ADR-0004](adr/0004-idempotency-outbox-retry.md) | 발송 등록 트랜잭션 commit 과 Kafka publish 를 한 트랜잭션으로 — DB rollback 시 phantom event 0 |
| **`SELECT … FOR UPDATE SKIP LOCKED`** | [`OutboxEventJpaRepository.findPending`](../notification-adapter-out/src/main/kotlin/com/example/notification/adapter/out/persistence/repository/OutboxEventJpaRepository.kt) | ADR-0004 | 여러 relay 인스턴스가 같은 PENDING row 를 겹쳐 발행하지 않게 행 잠금 |
| **채널별 topic fan-out** | per-channel Kafka topic (`notification.delivery.{channel}`) + consumer-group 분리 | [ADR-0002](adr/0002-channel-fanout-topics.md) | SMS/알림톡(저throughput·고비용)이 PUSH/EMAIL 을 head-of-line blocking(= 한 줄로 세우면 맨 앞의 느린 건이 뒤의 빠른 건까지 다 막는 현상) 하지 않게 격리 |
| **at-least-once + consumer dedup** | relay 발행 / consumer 측 `eventId` dedup | ADR-0004 | publish-then-mark 사이 crash 로 중복 발행 가능 → consumer 가 멱등 흡수 |

→ 이론: `dev-lab/kafka` (topic/partition/consumer-group), `dev-lab/cdc` (Outbox vs Debezium CDC), `dev-lab/postgresql` (`FOR UPDATE SKIP LOCKED` 잠금 모델)

## 멱등성 · 중복 방지

| 패턴 | 이 레포 어디서 | 왜 | 한 줄 |
|------|---------------|-----|-------|
| **Idempotency-Key (SETNX + TTL)** | [`RedisIdempotencyStore.kt`](../notification-adapter-out/src/main/kotlin/com/example/notification/adapter/out/redis/RedisIdempotencyStore.kt) ← [`NotificationController.kt`](../notification-adapter-in/src/main/kotlin/com/example/notification/adapter/in/rest/NotificationController.kt) | [ADR-0004](adr/0004-idempotency-outbox-retry.md) | `SET IF NOT EXISTS` + TTL 24h 로 같은 키 동시 2건 중 1건만 통과 (HTTP 409), 트랜잭션 진입 전 차단 → race 없음 |

→ 이론: `dev-lab/api-design` (idempotency key 설계 / 안전한 재시도), `dev-lab/redis` (SETNX 원자성)

## 회복탄력성 (Resilience)

| 패턴 | 이 레포 어디서 | 왜 | 한 줄 |
|------|---------------|-----|-------|
| **vendor 호출 retry (exp backoff + jitter)** | `@Retry` on [`MockFcmClient`](../notification-adapter-out/src/main/kotlin/com/example/notification/adapter/out/vendor/MockFcmClient.kt) / `MockSesClient` / `MockTwilioClient` / `MockKakaoAlimTalkClient` + `resilience4j.retry` (application.yml) | [ADR-0011](adr/0011-resilience4j-retry-tuning.md) | 200→400→800ms ±50% jitter — vendor stampede(thundering herd) 없이 단발성 5xx/IO 흡수 |
| **retry whitelist (transient vs permanent)** | [`VendorTransientException`](../notification-adapter-out/src/main/kotlin/com/example/notification/adapter/out/vendor/VendorTransientException.kt) / [`VendorPermanentException`](../notification-adapter-out/src/main/kotlin/com/example/notification/adapter/out/vendor/VendorPermanentException.kt) (`ignore-exceptions`) | ADR-0011 | 4xx(NOT_REGISTERED 등)는 즉시 fail — 무의미한 retry + vendor 비용 제거 |
| **2단 retry 경계 (호출 내부 vs attempt 단위)** | Resilience4j 3회 ↔ 도메인 `DeliveryAttempt.MAX_RETRY=5` ([`DispatchDeliveryService.kt`](../notification-application/src/main/kotlin/com/example/notification/application/service/DispatchDeliveryService.kt)) | ADR-0011, [ADR-0004](adr/0004-idempotency-outbox-retry.md) | 직렬 합산 3×5 — "호출 1회 즉시 재시도" 와 "attempt 단위 backoff 재발행" 명문 분리 |
| **HikariCP 명시 튜닝 + leak detection** | application.yml `spring.datasource.hikari` | [ADR-0008](adr/0008-hikaricp-tuning.md) | 풀 크기 산정 근거 명문화 + close 누락 30s 넘으면 stack trace 로그 |

> 참고: circuit breaker / bulkhead 는 아직 미도입 (ADR-0011 의 "다시 검토할 시점" 에 후속 과제로만 기록). 현재 회복탄력성은 retry + whitelist + 2단 경계까지.

→ 이론: `dev-lab/resilience` (retry / circuit breaker / bulkhead — 본 레포는 retry 단계), `dev-lab/networking` (커넥션 풀 sizing), `dev-lab/jvm` (HikariCP 와 thread 점유)

## Rate limiting

| 패턴 | 이 레포 어디서 | 왜 | 한 줄 |
|------|---------------|-----|-------|
| **recipient × channel token bucket (Redis Lua)** | [`RedisRateLimiter.kt`](../notification-adapter-out/src/main/kotlin/com/example/notification/adapter/out/redis/RedisRateLimiter.kt) ← [`SendNotificationService.kt`](../notification-application/src/main/kotlin/com/example/notification/application/service/SendNotificationService.kt) | [ADR-0006](adr/0006-rate-limit-token-bucket.md) | INCR+PEXPIRE 를 Lua 로 원자 처리, 채널별 차등 한도(SMS/알림톡 분당 5) — vendor 비용 / 스팸 신고 방지 |
| **admin endpoint rate limit** | [`RedisAdminRateLimiter.kt`](../notification-adapter-out/src/main/kotlin/com/example/notification/adapter/out/redis/RedisAdminRateLimiter.kt) | [ADR-0015](adr/0015-dlq-admin-api-v2.md) | 운영자 IP × scope 기준 한도 — bulk 작업 별도 카운터 |

→ 이론: `dev-lab/redis` (token bucket / Lua atomicity), `dev-lab/api-design` (429 + Retry-After)

## 보안 (API)

| 패턴 | 이 레포 어디서 | 왜 | 한 줄 |
|------|---------------|-----|-------|
| **HMAC-SHA256 webhook 콜백 검증** | [`HmacSignatureVerifier.kt`](../notification-adapter-in/src/main/kotlin/com/example/notification/adapter/in/security/HmacSignatureVerifier.kt) + [`WebhookSecrets.kt`](../notification-adapter-in/src/main/kotlin/com/example/notification/adapter/in/security/WebhookSecrets.kt) ← [`DeliveryAcknowledgeController.kt`](../notification-adapter-in/src/main/kotlin/com/example/notification/adapter/in/rest/DeliveryAcknowledgeController.kt) | [ADR-0014](adr/0014-hmac-webhook-callback-verification.md) | 콜백 URL 만 알아도 가짜 "전송 성공" 마킹 못 하게 vendor 별 secret 으로 서명 검증 (fail-closed) |

→ 이론: `dev-lab/api-design` (webhook 서명 / replay 방지 / idempotency), `dev-lab/observability` (audit 로그)

## 운영 / SRE

| 패턴 | 이 레포 어디서 | 왜 | 한 줄 |
|------|---------------|-----|-------|
| **DLQ 운영 REST API (list/replay/discard)** | `/api/v1/admin/dlq/...` (adapter-in rest) | [ADR-0012](adr/0012-dlq-admin-endpoint.md) | EXHAUSTED attempt 를 운영자가 조회 → PENDING 환원(재발행) / soft delete |
| **DLQ API v2 — filter / detail / stats / bulk(dry-run)** | 위 endpoint 확장 | [ADR-0015](adr/0015-dlq-admin-api-v2.md) | bulk 는 `confirm=false` dry-run 강제 + sample 10개 → confirm 후 비동기 job |
| **multi-device push fan-out + 영구 실패 자동 비활성화** | [`RegisterDeviceTokenService.kt`](../notification-application/src/main/kotlin/com/example/notification/application/service/RegisterDeviceTokenService.kt) / [`DispatchDeliveryService.kt`](../notification-application/src/main/kotlin/com/example/notification/application/service/DispatchDeliveryService.kt) | [ADR-0013](adr/0013-multi-device-push-fanout.md) | 한 사용자 N개 device 로 PUSH fan-out, NOT_REGISTERED 토큰은 자동 disable |
| **K8s 3종 probe + graceful shutdown** | application.yml `management.endpoint.health.group` + `ApplicationReadinessCoordinator` | [ADR-0009](adr/0009-k8s-probes.md), [ADR-0010](adr/0010-graceful-shutdown.md) | readiness 는 외부 의존(Kafka/Redis)까지, liveness 는 process alive 만 / SIGTERM 시 in-flight drain |

→ 이론: `dev-lab/incident-response` (DLQ replay 운영), `dev-lab/resilience` (영구 실패 격리)

## Spring Boot 심화

| 패턴 | 이 레포 어디서 | 한 줄 |
|------|---------------|-------|
| **헥사고날 + 6개 멀티모듈**(= 핵심 로직을 한가운데 두고 DB·Kafka·웹은 콘센트·플러그처럼 갈아끼우게 분리한 구조를, 역할별 모듈로 나눈 것) | `notification-domain / application / adapter-in / adapter-out / bootstrap` | [ADR-0001](adr/0001-hexagonal-architecture.md) — 의존 방향: adapter → application → domain |
| **Virtual Threads (Java 21)** | application.yml `spring.threads.virtual.enabled: true` | vendor 호출 다수 동시 처리 — blocking I/O 를 OS thread 점유 없이 (단, JDBC 는 여전히 OS thread → HikariCP 풀 산정에 반영) |
| **vendor adapter 공통 port** | [`DeliveryGateway.kt`](../notification-application/src/main/kotlin/com/example/notification/application/port/out/DeliveryGateway.kt) + 채널별 Mock client | [ADR-0007](adr/0007-vendor-adapter-port.md) — port 1개 + 채널별 adapter, SDK 교체가 도메인에 안 샘 |
| **템플릿 엔진 (Mustache)** | template 도메인 + 렌더링 | [ADR-0003](adr/0003-template-engine-mustache.md) — locale × channel 별 본문 분리, ko-kr fallback |

→ 이론: `dev-lab/system-design` (헥사고날 / 모듈 경계), `dev-lab/jvm` (Virtual Threads 와 carrier thread pinning)

## 학습 순서 제안 (이 레포 기준)

1. **README 상단 + 발송 흐름 다이어그램** → 요청 1건이 fan-out → Outbox → Kafka → vendor 까지 가는 전체 흐름
2. **[docs/adr/](adr/)** → 왜 그렇게 했나 (ADR 15건) ← 이 레포의 핵심 학습 자료
3. **위 패턴 표** 에서 관심 패턴 → 코드 위치 + 해당 ADR + dev-lab 이론
4. **`make run` 으로 직접 발송** → curl 한 사이클 (README "발송 한 사이클")
5. **`make demo`** → cross-repo 통합 흐름 (도메인 event → 발송 → vendor mock → sink)

> 짝 학습 레포: [dev-lab](https://github.com/ssa1004/dev-lab) (이론) ↔ 이 레포 (구현). 이론에서 "왜"를, 여기서 "실제로 어떻게"를 본다.
