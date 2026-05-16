# ADR-0015: DLQ 운영 API 확장 — filter / detail / stats / bulk

## 상태
적용. ADR-0012 (초기 단건 API) 를 보완하는 확장. 기존 endpoint 는 호환 유지.

## 배경
ADR-0012 에서 list / replay / discard 3개 단건 endpoint 로 시작. 그 뒤 운영하면서 드러난
한계:

- **필터 부재** — list 가 cursor + size 만 받음. 채널 / 시간 범위 / 에러 종류로 좁힐 수 없어
  EXHAUSTED 수천건 중에 사람이 눈으로 분류해야 함.
- **단건 detail 부재** — list 응답은 1줄 요약. 본문 (rendered title / body) / retry context
  (nextAttemptAt / vendorMessageId) / 마지막 vendor 에러 stacktrace 를 따로 봐야 함.
- **bulk 미지원** — vendor 가 1시간 죽었다 회복 → EXHAUSTED 가 수천 건. 하나씩 replay
  버튼을 누를 수 없음. 이전 운영자는 SQL 로 직접 status 갱신 + Kafka publish 흉내 → 사고 위험.
- **stats 부재** — 시간 / 채널 / 에러 종류별 추세를 한눈에 보고 어디서 문제가 가장 많이 났는지
  알 수 없음. 이게 없으면 운영자는 "체감" 으로 우선순위 판단.

## 결정
ADR-0012 의 3개 endpoint 는 그대로 유지 (path / 응답 호환). 다음 endpoint 추가:

| HTTP | path | 동작 |
|---|---|---|
| `GET` | `/api/v1/admin/dlq/search?channel=&topic=&consumerGroup=&from=&to=&errorType=&cursor=&size=` | 필터 + cursor 페이지네이션 |
| `GET` | `/api/v1/admin/dlq/{attemptId}` | 단건 detail (rendered body + retry context + errorClass) |
| `GET` | `/api/v1/admin/dlq/stats?from=&to=&bucket=PT1H` | 시간 bucket / 채널 / 에러 종류별 count |
| `POST` | `/api/v1/admin/dlq/bulk-replay` | filter 로 다건 replay |
| `POST` | `/api/v1/admin/dlq/bulk-discard` | filter 로 다건 discard |
| `GET` | `/api/v1/admin/dlq/bulk-jobs/{jobId}` | 비동기 bulk job 진행도 / 결과 |
| `DELETE` | `/api/v1/admin/dlq/{attemptId}` | 항상 거절 — soft delete (discard) 만 허용 |

### bulk 안전 — dry-run + confirm
- `POST bulk-*` 의 request body 에 `confirm: boolean` 이 있다. 기본값 false → dry-run 으로
  응답 (대상 개수 추정 + sample id 10개). 운영자가 sample 확인 후 `confirm=true` 로 재호출해야
  실 실행. 한 번에 수천건이 잘못 재발송되는 사고 방지.
- `bulk-discard` 는 `reason` 필수 (NotBlank). audit 에 항상 actor / reason / 대상 개수 기록.
- 실행 시 비동기 worker (1 thread pool) 가 batch 100건씩 처리. 각 항목은 별도 트랜잭션
  (`TransactionTemplate.execute` 호출) — 한 건 실패가 다른 건 롤백을 일으키지 않음 → partial
  failure 추적 가능.

### filter 의미
- `channel` — enum (`PUSH` / `EMAIL` / `SMS` / `KAKAO_ALIMTALK`). `topic` 과 같이 주면 `channel`
  우선.
- `topic` — `notification.delivery.<channel>` 형식 문자열. 알 수 없는 prefix 면 결과 0건.
- `consumerGroup` — 현 시스템은 channel 별 group 1개 (`notification-hub-<channel>`) 만 사용 →
  다른 값을 주면 결과 0건. 미래 multi-group 대비 호환 자리.
- `from` / `to` — ISO-8601 instant. `delivery_attempt.created_at` 범위.
- `errorType` — `failure_reason LIKE %v%`. detail 응답의 `errorClass` 도 같은 분류 기준.
- `cursor` — 이전 페이지 마지막 `attemptId`. id ASC. `nextCursor` null 이면 마지막 페이지.
- `size` — 1~200. 그 이상은 cursor 로 페이지.

### 권한 / 안전
- 권한 — ADR-0012 의 `AdminAuthFilter` + `AdminContext` 그대로. (Spring Security 미도입 결정
  유지 — ADR-0012 의 trade-off 그대로 적용.)
- audit — `AuditAction` = `DLQ_REPLAY` / `DLQ_DISCARD` / `DLQ_BULK_REPLAY_DRYRUN`
  / `DLQ_BULK_REPLAY_START` / `DLQ_BULK_REPLAY_FINISH` / `DLQ_BULK_DISCARD_*` 6종.
- rate limit — 호출자 IP × scope 별 token bucket (Redis Lua, ADR-0006 의 token bucket 패턴
  재사용). 기본 분당 60. scope = `dlq.read` / `dlq.write` / `dlq.bulk` 셋. 초과 시 HTTP 429 +
  `Retry-After` 헤더.
- idempotency — single replay 는 ADR-0012 의 도메인 상태 가드 (`EXHAUSTED` 가 아니면
  `IllegalDlqOperationException` → 409) 가 두 번 클릭 방지. bulk 의 같은 jobId 결과는 in-memory
  에 보존 (1시간 retention) — 운영자가 후속 확인 가능.

### 비동기 bulk worker 구현
- `dlqBulkExecutor` — Spring `ThreadPoolTaskExecutor` (core 1 / max 2 / queue 8). 동시 bulk
  실행은 사실상 1건만 허용 — vendor 부하 / outbox 폭주 방지. concurrency 늘릴 필요가 있으면
  다른 pod 가 받게 하는 방향.
- 결과 보존 — `DlqBulkJobRepository` (in-memory `ConcurrentHashMap`). 노드 재시작 시 진행 중인
  job 정보는 손실 — DB / Redis 로 옮기는 건 어댑터 한 개 교체로 충분. 운영자가 `bulk-jobs/{id}`
  로 진행도 폴링.

### stats 구현
- 어댑터는 group by 책임만 — `findForStats` 가 raw row 를 가져오고 use case 가 시간 bucket /
  채널 / errorClass 별로 묶음. DB-side aggregation 은 H2 / Postgres 호환 SQL 함수 (date_trunc
  등) 가 달라 후처리로 단일화. 운영자가 from / to 로 범위를 적절히 좁히면 row 수가 수천 단위
  유지되어 부담 X.
- bucket — ISO-8601 Duration (`PT1H` / `PT15M` 등). null 이면 1시간. from / to null 이면 최근
  24h.
- errorClass — `failure_reason` 의 첫 `:` 또는 ` ` 이전 토큰 (예: `VendorTransientException:
  vendor down` → `VendorTransientException`). 별도 enum 강제 없이 새 예외 타입이 자연스럽게
  분류.

## 결과
- **운영자가 사고 없이 DLQ 대량 처리 가능** — dry-run 으로 미리 보고 sample 확인 후 confirm.
- **partial failure 추적** — bulk job 의 successCount / failureCount / firstError 로 부분 실패
  대응. 한 건 실패가 다른 건을 롤백하지 않음.
- **호환 보존** — ADR-0012 의 path / 응답 그대로. 기존 운영 스크립트 / 대시보드 영향 X.
- **확산 가능한 표준** — 어댑터 단 교체로 다른 서비스 (billing / market / gpu / commerce-ops)
  에 동일 패턴 적용 가능. 도메인 의존이 없는 부분 (rate-limit / audit / dry-run / bulk worker)
  은 그대로 옮길 수 있음.
- (단점) **in-memory job 저장** — 노드 재시작 시 진행 중 job 정보 소실. 단건은 DB 에 status
  변경이 영속화되므로 일부 처리 완료된 항목까지는 보존됨 — 운영자가 정확한 진행도 추적이
  안 될 뿐. DB 어댑터 추가는 후속 작업.
- (단점) **stats 의 후처리 비용** — 큰 시간 범위 + 작은 bucket 조합은 row 수 폭증. 운영
  화면에서 기본 24h / 1h 로 제한 권고. DB-side bucket 함수 도입은 Postgres 전용으로 갈 때
  재검토 (현재 H2 호환 유지가 더 큰 가치).
- (단점) **Spring Security 미도입 그대로** — ADR-0012 의 trade-off 가 그대로 적용. multi-admin
  / per-actor audit / role 분리는 다음 ADR 에서 처리 예정.

## 다시 검토할 시점
- DLQ 일평균 1만 건 넘으면 bulk worker 1 thread 로는 처리량 부족 → executor concurrency 늘리고
  attempt-id 단위 distributed lock 추가.
- bulk job 의 in-memory 저장으로 인한 운영 혼선이 한 번이라도 발생하면 DB / Redis 로 즉시 이전.
- Spring Security + OIDC 도입 시 `@PreAuthorize("hasRole('ADMIN')")` 로 변환 + `AdminContext`
  제거. 동시에 token 단일 시크릿 (`admin.auth.token`) 폐기.
