# ADR-0004: Idempotency-Key + Outbox + retry 의 3중 안전망

## 상태
적용

## 배경
알림은 한 번 보내면 회수 불가하고 두 번 가면 사용자 신뢰가 깨지는 도메인입니다. 다음
3가지 실패 모드를 모두 막아야 함:

1. **클라이언트 retry 로 중복 요청** — 모바일 네트워크 단절, 사용자 더블클릭, 운영자 재실행
2. **DB commit 성공 + Kafka send 실패** — 도메인은 알림 등록되었는데 발송 이벤트 유실
3. **vendor 일시 장애** — FCM 5xx, SES throttling, 알림톡 timeout

## 결정
3개 layer 가 각자 자기 단계만 책임:

### (1) Idempotency-Key — 중복 요청 차단
- 호출자가 임의 문자열을 `Idempotency-Key` 헤더에 넣어 전달
- `RedisIdempotencyStore` 가 SETNX (`SET IF NOT EXISTS`) + TTL 24h 로 점유
- 같은 키가 동시에 두 번 들어오면 한 쪽만 true 반환 → 다른 쪽은 `DuplicateRequestException` (HTTP 409)
- DB 입장에선 트랜잭션 진입 자체가 차단되므로 race 없음

### (2) Outbox — DB commit ↔ Kafka send 원자성
- 도메인 트랜잭션 안에서 `outbox_event` 테이블에 INSERT 만 함
- 별도 `OutboxRelay` 가 polling 으로 PENDING row 를 가져가 Kafka publish 후 PUBLISHED 마킹
- send 실패 row 는 status PENDING 유지 → 다음 polling 에서 자동 재시도
- DB rollback 되면 outbox row 도 함께 사라짐 → phantom event 0

### (3) Vendor retry — vendor 일시 장애 흡수
- DeliveryAttempt 도메인이 retryCount + exponential backoff 로직 보유 (max 5)
- vendor 호출은 Resilience4j `@Retry(name="vendorXxx")` 로 단계 안에서도 3회 재시도
- 그래도 실패하면 attempt 가 PENDING 으로 복귀 + nextAttemptAt 미래 시각 → consumer 가
  poll 할 때 이를 보고 다시 dispatch
- max 5회 도달하면 EXHAUSTED → 운영자 수동 재처리 또는 보관

## 결과
- 같은 사용자에게 같은 키로 만 번 호출되어도 1번만 발송 (idempotency)
- Kafka 일시 장애 시 메시지 유실 0 건 (outbox)
- vendor 일시 장애 흡수 (3 + 5 = 최대 8회 재시도 기회)
- (단점) at-least-once 의 가능성 — relay 가 publish 직후 markPublished 직전에 죽으면 같은
  메시지가 두 번 publish 될 수 있음. consumer 측 `eventId` 기반 dedup 으로 흡수

## 다시 검토할 시점
- 처리량이 매우 늘어 polling relay 의 부담이 커지면 Debezium CDC 로 outbox 테이블을 source
  삼아 변환
- TTL 24h 가 너무 길거나 짧으면 운영 데이터 보고 조정 — 현재는 모바일 클라이언트의 최대
  retry 윈도우를 가정한 값

## 용어 풀이 (쉽게)

- **멱등성 / Idempotency-Key (멱등 키)** — 같은 요청이 두 번 와도 결과가 한 번 한 것과 똑같게. 요청마다 고유 표딱지(키)를 붙여, 같은 표딱지가 또 오면 새로 처리하지 않는다.
- **Outbox 패턴 (보낼 편지함)** — DB 저장과 Kafka 발송이 따로 놀아 한쪽만 성공하는 사고를 막으려, 보낼 메시지를 같은 트랜잭션 안에서 '편지함' 테이블에 같이 적고 나중에 일꾼이 꺼내 보내는 방법.
- **OutboxRelay / polling (릴레이·폴링)** — 그 편지함을 짧은 주기로 들여다보며 아직 안 보낸 편지를 꺼내 Kafka로 부치고 '보냄' 도장을 찍는 백그라운드 집배원.
- **SETNX (SET if Not eXists)** — "이 자리가 비어 있을 때만 내 이름표를 붙인다"를 한 번의 끊김 없는 동작으로. 여러 요청이 같은 키를 동시에 잡아도 딱 하나만 이긴다.
- **exponential backoff (지수 백오프)** — 재시도 간격을 1→2→4→8초처럼 점점 두 배로 늘려, 실패한 곳을 쉴 새 없이 두드려 더 망가뜨리지 않게 하는 방식.
- **at-least-once + dedup (최소 한 번 + 중복 제거)** — 메시지를 '적어도 한 번은 반드시' 보내되 가끔 두 번 올 수 있어, 받는 쪽이 eventId를 보고 "이거 아까 받았네" 하며 중복을 걸러내는 것.
- **CDC / Debezium** — DB를 주기적으로 훑는 대신, DB의 변경 기록을 실시간으로 흘려보내 잡아채는 기법. 우편함을 5분마다 확인하기 vs 도착 즉시 알림 받기.
