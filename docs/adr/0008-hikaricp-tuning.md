# ADR-0008: HikariCP 명시 튜닝 + leak detection

## 상태
적용

## 배경
Spring Boot 의 HikariCP 기본값은:

- `maximum-pool-size = 10` — 채널 4종 worker + outbox relay + REST 동시 요청에는 모자람.
  pool 고갈 시 `SQLTransientConnectionException: HikariPool-1 - Connection is not available,
  request timed out after 30000ms` 가 trace 가 끊긴 채 떨어짐.
- `connection-timeout = 30s` — DB 가 죽으면 호출 측이 30초 동안 멍 때림. fail-fast 가 안 됨.
- `leak-detection-threshold = 0` (꺼짐) — connection 을 try-with-resources / `@Transactional`
  바깥에서 받아 close 누락하면 pool 만 야금야금 새며 운영 중 디버깅 불가.

## 결정
`application.yml` 의 `spring.datasource.hikari.*` 를 명시 — 산정 근거는 yml 주석에 남김.

```yaml
hikari:
  maximum-pool-size: 20      # 채널 worker 4×4 + outbox 2 + REST 여유분
  minimum-idle: 5            # 트래픽 spike 직전 idle 유지
  connection-timeout: 3000   # vendor RTT < 100ms 가정 → 3s 안에 못 받으면 fail-fast
  max-lifetime: 1740000      # 29분 — DB 측 idle close 보다 짧게 (firewall 30분, PG 30분)
  idle-timeout: 600000       # 10분 — minimum-idle 위 conn 만 회수
  leak-detection-threshold: 30000  # close 누락 30s 넘으면 stack trace
  pool-name: notification-hub-pool
```

test profile 은 `leak-detection-threshold: 0` — Testcontainers tear-down 이 connection
close 보다 늦으면 false positive 로 로그가 시끄러워짐.

prod profile 의 `maximum-pool-size` / `minimum-idle` 은 ENV 로 오버라이드 가능
(`DB_POOL_MAX`, `DB_POOL_MIN_IDLE`) — 부하 테스트 결과에 맞춰 조정.

## 결과
- **fail-fast** — DB 죽으면 3s 안에 호출 측에 503 으로 알림 → 호출 측 retry 정책이 동작.
  기존 30s 타임아웃은 호출 측 timeout 보다 길어 "왜 hang 인지" 추적 어려움.
- **connection leak 추적 가능** — 30s 넘는 connection 은 pool 이 stack trace 로그
  (`Apparent connection leak detected`). 운영 시 `try-with-resources` 누락이나
  `@Transactional(propagation=NEVER)` 후 직접 connection 사용 같은 실수가 즉시 표출.
- **DB 측 idle close 와 충돌 회피** — `max-lifetime` 29분 < 일반 firewall idle 30분 / PG
  `idle_in_transaction_session_timeout` 기본값. close 된 connection 을 잡고 EOF 받는 시나리오 제거.
- (단점) pool 크기를 잘못 잡으면 — 너무 크면 DB CPU saturation, 너무 작으면 application
  idle 대기. 부하 테스트 → ENV 조정 사이클이 항상 필요.

## 다시 검토할 시점
- 채널 worker 동시 처리 수가 변경 (예: 8 채널, 채널당 동시 16) → `maximum-pool-size`
  재계산. PostgreSQL `max_connections` (기본 100) 를 넘지 않도록 application 인스턴스 수 ×
  pool 크기를 항상 < 80 으로 유지.
- DB 측 firewall / `wait_timeout` 가 변경되면 `max-lifetime` 도 같이 조정.
- 일반 read 트래픽이 10배 늘면 read replica + 별도 datasource 분리 검토 (`@Transactional
  (readOnly=true)` 라우팅).

## 용어 풀이 (쉽게)

- **HikariCP / connection pool (커넥션 풀)** — DB 연결을 매번 새로 만들지 않고 미리 몇 개 만들어 돌려쓰는 '연결 대여소'. HikariCP는 그걸 관리하는 도구 이름.
- **pool 고갈 (pool exhaustion)** — 빌려줄 연결이 다 떨어져 새 요청이 줄 서서 기다리다 결국 실패하는 상태. 우산 대여소에 우산이 동나 다음 손님이 발이 묶이는 셈.
- **connection leak (커넥션 누수)** — 빌린 연결을 다 쓰고도 반납을 깜빡해 대여소가 야금야금 비는 것. leak detection은 일정 시간 안 돌아온 연결을 추적해 범인을 알려준다.
- **fail-fast (빠른 실패)** — DB가 죽었을 때 30초씩 멍하니 기다리지 않고 3초 안에 곧장 실패로 알려, 호출 측이 빨리 재시도하게 하는 것.
- **max-lifetime** — 연결 하나의 최대 수명. 방화벽이나 DB가 오래된 연결을 먼저 끊어버리기 전에, 우리가 먼저 갈아끼워 '끊긴 연결을 잡고 있다 터지는' 사고를 막는다.
- **read replica (읽기 복제본)** — 읽기 요청만 받는 DB 사본. 조회가 폭증하면 원본은 쓰기에 집중하고 읽기는 복제본으로 돌려 부담을 나눈다.
