# ADR-0011: Resilience4j retry — exponential backoff + jitter, vendor 별 분리

## 상태
적용

## 배경
초기 retry config 는 단순했음 — `max-attempts: 3, wait-duration: 200ms` 고정. 문제:

- **fixed wait** — vendor 가 대규모 장애 (전체 5xx) 일 때 모든 pod / 모든 호출이 정확히
  200ms 후에 동시 재호출 → vendor 측 stampede → 복구 더 느려짐.
- **jitter 없음** — 같은 burst 안의 모든 호출이 같은 시점에 retry 함. 작은 vendor 일수록
  thundering herd 영향이 큼.
- **whitelist 모호함** — `VendorTransientException` 만 retry. 4xx 와 5xx 의 구분이 mock
  구현체에 묻혀있어서 새 vendor adapter 가 잘못 던지면 4xx 도 retry → 무의미한 호출 + 비용.
- **outbox 재시도와 의도 충돌** — 도메인 `DeliveryAttempt.MAX_RETRY=5` 와 Resilience4j 의
  3회가 왜 다른지 명문화 안 됨.

## 결정
### 1. 단계별 retry 책임 명문화
| 단계 | 책임 | 회수 | wait |
|---|---|---|---|
| Resilience4j (adapter-out) | 한 vendor 호출 내부의 단발성 5xx/IO 흡수 | 3회 | 200ms → 400ms → 800ms (exp 2.0) ± 50% jitter |
| Domain `markFailed` (notification-application) | attempt 단위 retry — vendor 가 정말 못 받는 큰 장애 | 5회 (`MAX_RETRY=5`) | 1s → 2s → 4s → 8s → 16s (exp 2.0, cap 60s) |

직렬 합산: 한 Outbox row 발행이 최종 EXHAUSTED 까지 갈 수 있는 vendor 호출 수 = 3 × 5 = 15.
실제 시간은 attempt 단위 backoff 가 dominate (수십 초 ~ 분 단위).

### 2. exponential backoff + jitter
```yaml
fcm: &vendor-retry
  max-attempts: 3
  wait-duration: 200ms
  enable-exponential-backoff: true
  exponential-backoff-multiplier: 2.0
  enable-randomized-wait: true
  randomized-wait-factor: 0.5    # ±50% jitter
```
YAML anchor `&vendor-retry` 로 ses/twilio/kakao 가 같은 설정을 inherit. vendor 별 SLA 다르면
override.

### 3. retry-exceptions whitelist
```yaml
retry-exceptions:
  - com.example.notification.adapter.out.vendor.VendorTransientException
  - java.io.UncheckedIOException
  - java.io.IOException
ignore-exceptions:
  - com.example.notification.adapter.out.vendor.VendorPermanentException
```
- whitelist: 5xx 또는 network. mock 은 `UncheckedIOException` 으로 IO 시뮬.
- ignore: 4xx (NOT_REGISTERED, INVALID_NUMBER, TEMPLATE_NOT_FOUND 등) — 즉시 fail.
- 그 외 모든 RuntimeException 도 retry 안 됨 (whitelist 모드는 명시된 것만 retry).

### 4. mock vendor 의 실패 시뮬 다양화
실패율 발생 시 1/3 확률로 4xx (`VendorPermanentException`), 1/3 5xx
(`VendorTransientException`), 1/3 network (`UncheckedIOException`) — Resilience4j 의 retry
경로가 모든 분기에 노출되어 통합 테스트로 검증 가능.

## 결과
- **vendor stampede 완화** — exp + jitter 로 retry 시점이 호출별로 분산. vendor 측 복구
  중인 시점에 무차별 호출 폭증 회피.
- **무의미한 retry 제거** — 4xx 는 즉시 fail → vendor 비용 + 호출 측 latency 절감.
- **두 retry 경계 명문화** — adapter-out 의 짧은 retry 와 도메인의 긴 retry 가 직렬임을
  ADR 에 못 박아 새 vendor 추가 시 헷갈림 방지.
- **mock 이 더 현실적** — failure-rate>0 으로 띄우면 Resilience4j 의 retry / ignore /
  whitelist 분기가 모두 자연스럽게 동작 — 인위적 단위 테스트 없어도 통합 테스트에서 표면화.
- (단점) jitter 가 있어 한 호출의 최대 대기 시간이 200ms × 1.5 + 400ms × 1.5 + 800ms × 1.5
  = 약 2.1s 까지 늘 수 있음. p99 latency 의 tail 이 늘어남. 호출 측 timeout 산정에 반영 필요.
- (단점) `IOException` 은 dispatch 시그니처상 unchecked 로 감싸야 (`UncheckedIOException`)
  Resilience4j 에 잡힘. 실제 vendor SDK 가 unchecked 로 wrap 안 하면 adapter 단에서 1단계
  변환 필요.

## 다시 검토할 시점
- vendor 별 SLA 가 명확히 다르면 (예: 알림톡 vendor 가 SLA 가 더 짧음) `&vendor-retry`
  anchor 를 풀고 vendor 별 wait/multiplier 별도 조정.
- circuit breaker 도입 (vendor 가 50% 이상 실패하면 한동안 호출 자체 차단) → 현재 retry 만
  있음. 별도 ADR 로 추가 검토.
- vendor SDK 의 retry 정책 (예: AWS SDK 의 SdkRetryStrategy) 와 중복되면 한쪽 끄기.

## 용어 풀이 (쉽게)

- **exponential backoff (지수 백오프)** — 재시도 간격을 200ms→400ms→800ms처럼 점점 두 배로 벌리는 것. 실패한 곳을 쉴 새 없이 두드려 더 망가뜨리지 않게 한다.
- **jitter (지터, 무작위 흔들기)** — 재시도 시점에 무작위 오차를 살짝 섞어 시각을 흩뜨리는 것. 안 그러면 실패한 모든 호출이 똑같은 순간에 다시 몰려든다.
- **thundering herd / stampede (떼몰림)** — 여러 호출이 똑같은 순간에 한꺼번에 몰려 회복 중인 서버를 다시 짓밟는 현상. 넘어진 사람에게 모두 동시에 손을 뻗어 더 깔리는 격. jitter가 이걸 막는다.
- **whitelist / ignore exceptions (재시도 화이트리스트)** — 다시 시도할 가치가 있는 오류(일시적 5xx·네트워크)만 골라 재시도하고, 고쳐도 소용없는 오류(잘못된 번호 같은 4xx)는 즉시 포기하는 것.
- **p99 latency tail (꼬리 지연)** — 100건 중 가장 느린 1건쯤의 응답 시간. 재시도로 가끔 한 호출이 길어지면 이 '꼬리'가 늘어난다.
- **YAML anchor (`&`)** — 설정 한 덩어리에 이름표(`&vendor-retry`)를 붙여 두고 다른 vendor가 그대로 가져다 쓰게 하는 '복사 안 하고 재사용' 문법.
