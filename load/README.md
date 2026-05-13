# Load test (k6)

notification-hub 의 5 가지 부하 시나리오. 다채널 fan-out / rate limit / webhook HMAC 같은
ADR 정책의 동작 검증과 throughput / latency 측정을 함께 본다. 단순 발송 RPS 만이 아니라
"한 발송이 N 채널로 atomic 하게 fan-out 되는가", "한도 초과가 정확한 코드로 거절되는가",
"vendor 서명 검증이 부하 상황에서도 fail-closed 인가" 를 회귀 가드한다.

## 디렉토리

```
load/
├── README.md
└── k6/
    ├── lib/
    │   ├── auth.js          # mock JWT 헬퍼 (K6_TOKEN env)
    │   └── config.js        # BASE URL + recipient/vendor pool + idempotency key 생성
    └── scenarios/
        ├── notify-single-channel.js   # POST /api/v1/notifications PUSH 단일 채널
        ├── notify-multi-channel.js    # 4 채널 fan-out — atomic rate limit 검증
        ├── ratelimit-saturation.js    # 단일 recipient 한도 트리거 + 거절 응답 정합성
        ├── history-cursor.js          # GET /api/v1/notifications/me cursor 분기 회귀 가드
        └── webhook-callback.js        # vendor ack — HMAC 검증 + replay window
```

## 사전 준비

세 가지 방법 중 하나:

### A. brew 로 로컬 설치

```bash
brew install k6
k6 version
```

### B. docker 직접 실행

```bash
docker run --rm -i grafana/k6 run - < load/k6/scenarios/notify-single-channel.js
```

### C. docker-compose profile (별도 설정 시)

본 저장소의 docker-compose 에는 k6 profile 이 따로 묶여 있지 않다. 운영자가 부하 측정용
profile 을 추가하려면 `grafana/k6` 이미지를 service 로 붙이고 host 의 `load/` 디렉토리를
volume mount 하는 형태가 가장 단순.

## 통합 환경 기동

본 부하 시나리오는 docker-compose 의 통합 환경 endpoint 를 가정한다.

### dev 단독 (H2 + Mock vendor)

```bash
./gradlew :notification-bootstrap:bootRun
# → http://localhost:8080
```

### prod 프로필 (Postgres + Redis + Kafka)

```bash
docker compose -f infrastructure/docker-compose.yml up -d postgres redis kafka kafka-ui
SPRING_PROFILES_ACTIVE=prod ./gradlew :notification-bootstrap:bootRun
```

### 통합 환경 (cross-repo demo — port 8088)

```bash
docker compose -f infrastructure/docker-compose.integration.yml up -d
./scripts/integration-demo.sh   # recipient/preference/device seed 1명
BASE_URL=http://localhost:8088 k6 run load/k6/scenarios/notify-single-channel.js
```

### recipient seed (부하용 N 명)

본 hub 의 발송은 사전 등록된 recipient + UserPreference 가 있어야 정상 응답이 떨어진다.
`scripts/integration-demo.sh` 가 한 명 (`demo-user-1`) 만 seed 하므로, 부하 시나리오가
풀로 사용하는 `demo-user-1..8` 은 별도로 채워야 한다 (운영자가 직접 SQL 또는 시드 스크립트
추가). seed 가 없으면 발송 요청이 모두 404 RECIPIENT_NOT_FOUND 로 떨어지므로 시나리오의
`http_req_failed` threshold 가 빨갛게 보인다 — 이건 부하 환경 미완성 신호로 해석.

## 시나리오별 실행

### 1) notify-single-channel — PUSH 단일 채널 발송

호출 서비스 (auth/billing/orderbook 등) 의 보안 알림 (OTP) 처럼 단일 채널만 가는 경로의
가장 빠른 path 측정. SECURITY kind 라 사용자 preference / DND 와 무관하게 일관된 분기.

```bash
k6 run load/k6/scenarios/notify-single-channel.js
```

| metric | 기준 |
|---|---|
| `http_req_duration` p95 / p99 | < 100ms / < 250ms |
| `http_req_failed` | < 1% |
| `notif_send_accepted` | > 95% (202 + 200 SUPPRESSED) |

### 2) notify-multi-channel — 4 채널 fan-out

한 발송이 PUSH/EMAIL/SMS/KAKAO_ALIMTALK 모두로 동시 전파. DB INSERT N 배, Outbox publish
N 배, 그리고 **atomic 묶음 rate limit** 의 동작이 핵심 검증 포인트. 한 사용자에 부분
leak (PUSH 통과 / SMS 거절) 이 발생하면 안 됨 — `notif_multi_partial_leak` counter 가 0
이어야 한다.

```bash
k6 run load/k6/scenarios/notify-multi-channel.js
```

| metric | 기준 |
|---|---|
| `http_req_duration` p95 / p99 | < 300ms / < 700ms |
| `http_req_failed` | < 5% (429 는 정상 — 한도 초과는 의도된 부하) |
| `notif_multi_accepted` | > 50% (한도 부딪치는 부하 모델 안에서의 성공률) |
| `notif_multi_partial_leak` | == 0 |

### 3) ratelimit-saturation — 단일 recipient 한도 트리거

한 사용자에게 초당 100 req 발사. 첫 ~30 발송 (PUSH 한도 30/분) 후 나머지는 모두 429.
HTTP 응답 코드 + Retry-After 헤더 + body 의 code 가 정확히 정합 한지를 본다.

```bash
k6 run load/k6/scenarios/ratelimit-saturation.js
```

| metric | 기준 |
|---|---|
| `ratelimit_reject_ratio` | > 95% (60s 총 6000 req 중 30 통과 → 99.5% 거절 기대) |
| `ratelimit_retry_after_present` | > 99% (모든 429 가 Retry-After 헤더 동반) |
| `ratelimit_code_correct` | > 95% (body 의 code = RATE_LIMIT_EXCEEDED) |
| `ratelimit_unexpected_5xx` | < 10 (한도 초과가 서버 에러를 유발하면 안 됨) |

### 4) history-cursor — 이력 조회 + cursor=null 회귀 가드

GET `/api/v1/notifications/me` 의 두 분기 (cursor 없는 첫 페이지 + cursor 있는 다음 페이지)
를 매 iteration 모두 호출. **commit `ee7d3a8` 의 회귀 가드** — `cursor` 가 null 일 때
Postgres JdbcTemplate 의 NULL 파라미터 타입 추론 실패로 500 이 떨어지던 이슈가 다시 깨지면
즉시 threshold 가 빨갛게 변한다.

```bash
k6 run load/k6/scenarios/history-cursor.js
```

| metric | 기준 |
|---|---|
| `http_req_duration` p95 / p99 | < 150ms / < 400ms |
| `http_req_failed` | < 1% |
| `history_no_cursor_ok` | > 99% (cursor=null 분기의 200 비율 — 핵심 회귀 가드) |
| `history_with_cursor_ok` | > 99% (cursor 있는 분기의 200 비율) |

### 5) webhook-callback — vendor ack HMAC 검증

vendor (FCM/SES/Twilio/Kakao) 의 콜백 모사 — `POST /api/v1/deliveries/{id}/ack` (Helm
ingress 에서 `/webhooks/*` prefix 로 노출). 한 시나리오 안에 세 경로를 섞어 흘린다:

- **80% 정상 서명** — 검증 통과 path 의 latency 측정. ATTEMPT_NOT_FOUND (404) 응답이 와도
  검증 단계는 통과한 신호로 간주.
- **10% replay (timestamp -10분)** — `REPLAY_WINDOW_MS` (5분) 밖이라 401 정상.
- **10% 서명 변조 (잘못된 secret)** — fail-closed 401 정상.

vendor 별 secret 은 운영 helm secret 과 동일 값을 env 로 주입해야 진짜 endpoint 를 통과:

```bash
K6_WEBHOOK_SECRET_FCM=fcm-secret \
K6_WEBHOOK_SECRET_SES=ses-secret \
K6_WEBHOOK_SECRET_TWILIO=twilio-secret \
K6_WEBHOOK_SECRET_KAKAO=kakao-secret \
  k6 run load/k6/scenarios/webhook-callback.js
```

| metric | 기준 |
|---|---|
| `http_req_duration{name:webhook-valid}` p95 / p99 | < 50ms / < 150ms |
| `webhook_hmac_fail` | == 0 (정상 서명은 단 한 건도 401 가면 안 된다) |
| `webhook_replay_rejected` | > 99% (윈도우 밖 timestamp 는 모두 401) |
| `webhook_signature_tamper_rejected` | > 99% (잘못된 서명은 모두 401) |

## 알림 특유 측정 항목

본 hub 가 단순 REST 백엔드와 다른 측정 항목:

| metric | 의미 |
|---|---|
| `notif_send_accepted` | 발송 요청의 정상 응답 비율 (202 + 200 SUPPRESSED). preference / DND / 한도 등 정책 분기의 합산 결과. |
| `notif_multi_accepted` | 다채널 fan-out 발송의 정상 응답 비율. 한도 부딪침 비율과 묶어 fan-out path 의 건강 상태를 본다. |
| `notif_multi_partial_leak` | atomic 묶음 rate limit 이 깨졌을 때만 증가. 0 이 아닌 모든 값은 회귀 신호. |
| `notif_multi_ratelimited` | 다채널 발송 중 429 카운트. 한도 도달 동작이 의도대로 트리거 됐는지의 raw count. |
| `ratelimit_reject_ratio` | over-limit 거절 비율 — 한도 트리거 자체가 정확히 동작하는가. |
| `ratelimit_retry_after_present` | 429 응답의 Retry-After 헤더 부착 비율. 호출자의 backoff 결정 가능성. |
| `ratelimit_code_correct` | 429 body 의 `code=RATE_LIMIT_EXCEEDED` 정합성. 호출자가 응답 종류를 정확히 분기할 수 있는가. |
| `ratelimit_unexpected_5xx` | 한도 초과가 서버 에러로 새지 않는가의 가드. |
| `history_no_cursor_ok` | cursor=null 분기의 200 비율. Postgres JdbcTemplate NULL 추론 회귀 가드의 핵심. |
| `history_with_cursor_ok` | cursor 있는 분기의 200 비율. 페이지네이션 정상 동작. |
| `webhook_hmac_fail` | 정상 서명이 401 로 거절된 비율 — 정상 path 의 검증 회귀. |
| `webhook_replay_rejected` | replay (윈도우 밖) 거절 비율. fail-closed 가 흐트러지면 즉시 감지. |
| `webhook_signature_tamper_rejected` | 잘못된 서명 거절 비율. 동일하게 fail-closed 신호. |
| `webhook_valid_processed` | 정상 서명이 검증 단계 통과한 count (ATTEMPT_NOT_FOUND 포함). |
| `history_cursor_followed` | 두 번째 페이지를 호출한 iteration count — 시나리오 진행 sanity. |

## 환경변수

| key | 기본 | 설명 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | HTTP base. 통합 compose 는 `http://localhost:8088`. |
| `K6_TOKEN` | (빈 값) | 운영 환경에서 Authorization 헤더로 부착할 Bearer 토큰. dev / 통합 환경은 비워둠. |
| `K6_RECIPIENTS` | `demo-user-1..8` | 발송 시나리오의 recipient pool — CSV. |
| `K6_SATURATION_RECIPIENT` | `demo-user-1` | ratelimit-saturation 의 단일 recipient. |
| `K6_WEBHOOK_SECRET_FCM` | `load-test-secret` | webhook 시나리오의 FCM HMAC secret. 운영 helm secret 과 동일 값으로 주입. |
| `K6_WEBHOOK_SECRET_SES` | `load-test-secret` | 위와 동일 — SES |
| `K6_WEBHOOK_SECRET_TWILIO` | `load-test-secret` | 위와 동일 — Twilio |
| `K6_WEBHOOK_SECRET_KAKAO` | `load-test-secret` | 위와 동일 — Kakao |

## k6 metric 해석 (참고)

| metric | 의미 |
|---|---|
| `vus` / `vus_max` | 현재 / 최대 VU |
| `iter_duration` / `iteration_duration` | 한 default 함수 실행 시간 |
| `http_req_duration` | HTTP 응답 소요 — connect / TLS / waiting 합 |
| `http_req_waiting` | TTFB (server-side latency 의 근사) |
| `http_req_failed` | non-2xx 비율 |
| `data_received` / `data_sent` | byte 카운터 |

### p95 / p99 보는 법

- **p95** 는 일상 SLO 의 변동성 신호 (95 백분위).
- **p99** 는 꼬리 신호 — GC, R2DBC/HikariCP 풀 고갈, Redis Lua 라운드트립 스파이크 등 드문 이벤트.
- p95 → p99 격차가 크면 운영 환경의 reliability tail 이 두꺼운 것 — 풀 크기 / GC tuning /
  vendor timeout 부터 본다.

### 시나리오별 부하 모델

| 시나리오 | executor | rate / VU |
|---|---|---|
| notify-single-channel | constant-arrival-rate | 200 req/s, 60s, preAllocated 50 |
| notify-multi-channel | constant-arrival-rate | 100 req/s, 60s, preAllocated 40 |
| ratelimit-saturation | constant-arrival-rate | 100 req/s, 60s, preAllocated 30 |
| history-cursor | constant-arrival-rate | 150 req/s × 2 GET, 60s, preAllocated 30 |
| webhook-callback | constant-arrival-rate | 500 req/s, 45s, preAllocated 50 |

모두 `constant-arrival-rate` — throughput / latency 측정이 목적이라 connection-bound
변동이 적은 모델을 선택했다. WS / SSE 가 없는 hub 라 ramping-vus 가 필요한 시나리오는
이번 라운드에는 없다.

## 결과 예시 (참고 — 환경마다 다름)

m1 max + docker-compose 통합 (Postgres + Redis + Kafka, 1 instance, 4 cpu, 2G heap) 기준
대략적인 예시:

```
notify-single-channel
  http_req_duration........... avg=22ms     p(95)=58ms   p(99)=140ms
  http_req_failed............. 0.05%
  notif_send_accepted......... 99.8%

notify-multi-channel (8 명 풀 × 4 채널)
  http_req_duration........... avg=80ms     p(95)=210ms  p(99)=520ms
  http_req_failed............. 1.2%
  notif_multi_accepted........ 68%
  notif_multi_ratelimited..... 1900 / 6000 (32%)
  notif_multi_partial_leak.... 0

ratelimit-saturation (단일 recipient)
  http_req_duration........... avg=12ms     p(95)=42ms
  ratelimit_reject_ratio...... 99.5%
  ratelimit_retry_after_present 100%
  ratelimit_code_correct...... 100%

history-cursor (cursor 분기 두 경로)
  http_req_duration........... avg=18ms     p(95)=66ms   p(99)=170ms
  history_no_cursor_ok........ 100%
  history_with_cursor_ok...... 100%

webhook-callback (HMAC 80/10/10)
  http_req_duration{name:webhook-valid}
                              avg=8ms      p(95)=22ms   p(99)=58ms
  webhook_hmac_fail........... 0%
  webhook_replay_rejected..... 100%
  webhook_signature_tamper_rejected
                              100%
```

## 더 나아가려면

- 5 시나리오의 결과를 `build/k6-reports/*.json` 으로 떨궈서 dashboard 에 plot.
- `--out experimental-prometheus-rw=http://prom:9090/api/v1/write` 로 Prometheus remote-write
  연결 후 Grafana 에 plot — k6 metric 과 hub 의 actuator/prometheus metric 을 같은 시간축에
  올리면 부하 시점의 풀 사용률 / Outbox lag / Kafka consumer lag 도 함께 본다.
- 더 큰 부하는 k6 cloud / k6 distributed mode 필요 — 본 시나리오는 single-node 기준이라
  VU 100 ~ 200 선 운용.
- recipient 풀 seed 전용 스크립트 분리 — 본 hub 의 recipient/preference/device seed 는 현재
  통합 시연용 (`scripts/integration-demo.sh`) 만 있다. 부하 측정 전용 seed 스크립트가
  있으면 더 깔끔.
