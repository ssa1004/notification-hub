# ADR-0012: DLQ 운영 endpoint — list / replay / discard

## 상태
적용

## 배경
DeliveryAttempt 가 5회 retry 후 EXHAUSTED 가 되면 자동 처리는 끝. 그 다음:

- 운영자가 EXHAUSTED 항목의 실패 사유를 보려면 DB 직접 조회 → 조작 사고 위험.
- 일시 장애 (vendor 가 1시간 죽었다가 회복) 후 EXHAUSTED 된 알림을 수동 재발송할 수단 필요.
- 영구 무의미 (이미 알림 시점이 지난 OTP 등) 인 항목은 audit 만 남기고 다시 시도하지 않음을
  명시할 수단 필요.

## 결정
3개 운영 endpoint. 모두 `/api/v1/admin/dlq` 아래 + `X-Admin-Token` 헤더 검증 (`AdminAuthFilter`).

| HTTP | path | 동작 |
|---|---|---|
| `GET` | `/api/v1/admin/dlq?cursor=&limit=` | EXHAUSTED 항목 cursor 페이지네이션 (id ASC) |
| `POST` | `/api/v1/admin/dlq/{attemptId}/replay` | EXHAUSTED → PENDING(retry=0), Outbox 재발행 |
| `POST` | `/api/v1/admin/dlq/{attemptId}/discard` | EXHAUSTED → PERMANENTLY_FAILED, audit 만 |

### 도메인 변경
- `DeliveryStatus.PERMANENTLY_FAILED` 추가 — replay 불가 final 상태.
- `DeliveryAttempt.replayFromExhausted()` / `discardFromExhausted(reason)` — EXHAUSTED 에서만
  호출 가능. 다른 상태에서 호출하면 `IllegalStateException`.

### 권한
- `AdminAuthFilter` (OncePerRequestFilter) 가 `/api/v1/admin/**` 요청만 `X-Admin-Token`
  검증. `MessageDigest.isEqual` 로 timing-safe 비교.
- `AdminContext` (ThreadLocal) 에 admin 여부 세팅. use case 가 `requireAdmin()` 으로 가드 →
  `UnauthorizedAdminException` (HTTP 401).
- `admin.auth.token` 미설정이면 모든 admin 요청 거절 (default-deny).
- 향후 Spring Security + OIDC + role 기반으로 교체 — 현재는 학습 단계 단순 가드.

### replay 시 흐름
1. `replayFromExhausted()` → status=PENDING, retry=0
2. `repository.save()` (트랜잭션 안)
3. `OutboxPublisher.publish()` — Outbox 테이블에 INSERT (같은 트랜잭션)
4. 트랜잭션 commit → OutboxRelay 가 다음 polling 에서 Kafka 로 push
5. 채널 worker 가 consume → DispatchDeliveryService.dispatch() → vendor 호출

### discard 시 흐름
1. `discardFromExhausted(reason)` → status=PERMANENTLY_FAILED, failureReason 에 reason append
2. `repository.save()`
3. AuditLogger 에 DLQ_DISCARD 기록 (이벤트 발행 안 함 — Kafka 부하 줄임)

## 결과
- **운영자가 SQL 없이 DLQ 조회 + 작업 가능** — DB 직접 조작 사고 회피.
- **replay 가 idempotent** — 같은 attemptId 를 두 번 replay 해도 두 번째는 EXHAUSTED 가
  아니라서 `IllegalDlqOperationException` (HTTP 409). 즉 "버튼 두 번 클릭" 사고 방지.
- **discard 의 audit trail** — `failureReason` 에 "discarded: <reason>" append 되어 사후
  감사에 사용. status 가 PERMANENTLY_FAILED 면 worker 가 isFinal() 로 즉시 skip.
- **Spring Security 미도입** — token 기반 가벼운 가드. cross-site / replay 보호는 X. 운영
  네트워크 boundary 안에서만 노출 가정.
- (단점) `X-Admin-Token` 이 평문 — TLS 외부 접근은 무조건 HTTPS. 토큰 누출 시 권한 박탈
  방법 = 환경 변수 새 토큰으로 교체 + redeploy.
- (단점) admin 식별이 token 단위 — 누가 discard 했는지 audit 에 남지 않음. 향후
  Authorization header 의 JWT subject 로 actor 분리.

## 다시 검토할 시점
- multi-admin 환경 (DevOps 팀 / 보안팀 / CS 팀 분리) 으로 가면 Spring Security + OIDC +
  role 기반 인가로 마이그레이션. token 단일 시크릿은 1팀 전용으로만 적합.
- DLQ 가 일평균 1만건 넘으면 cursor 페이지네이션 만으론 부족 → 채널 / kind / 시간 범위 필터.
- discard 가 일정 비율 (예: 10%) 넘으면 fan-out 정책에 문제 — 자동 monitoring + 알림.
