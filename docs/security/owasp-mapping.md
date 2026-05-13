# OWASP API Security Top 10 (2023) 매핑

알림 hub 의 모든 API surface 를 OWASP API Top 10 (2023) 항목에 직접 매핑한다. 학습 목적의
저장소이므로 일부 가드는 의도적으로 단순화되어 있다 — 그 부분은 "현재 상태" 와 "운영
이행 가이드" 를 같이 적었다.

| 항목 | 영역 | 현재 상태 | 비고 |
|---|---|---|---|
| API1 BOLA | 알림 이력 / 선호도 / 디바이스 토큰 | 부분 — 식별자 신뢰 | JWT subject 연동 후속 |
| API2 Broken Authentication | REST 진입점 | 미구현 (auth-service 통합 전 단계) | 게이트웨이 / 리소스 서버 후속 |
| API3 Broken Object Property Auth | 알림 본문 / 페이로드 | 길이 제약 + audit 분리 | 민감 payload masking 후속 |
| API4 Unrestricted Resource Consumption | rate limit / pagination / fan-out | 다층 가드 | 디바이스 토큰 상한 후속 |
| API5 Broken Function Level Auth | `/api/v1/admin/**` | 정적 토큰 + default-deny | role 기반 후속 |
| API6 Unrestricted Sensitive Flow | vendor 발송 / opt-out / DND | rate limit + opt-out + 야간 차단 | abuse 모니터링 후속 |
| API7 SSRF | webhook 콜백 / 이미지 프록시 | 외부 URL fetch 표면 없음 | 향후 templating 시 재검토 |
| API8 Security Misconfig | secret / CORS / 기본값 | env 평문 → Vault 권장 + CORS 제한 | prod 강제 점검 |
| API9 Improper Inventory | API surface | path-prefix 분리 + OpenAPI 게시 | deprecated v0 없음 |
| API10 Unsafe API Consumption | vendor mock / 실 vendor 응답 | DTO 길이 가드 적용 (이번 변경) | 다음 절 참조 |

다음 절은 각 항목의 진입점 (코드 위치) / 현재 가드 / 한계 / 운영 이행 순으로 정리한다.

---

## API1 — Broken Object Level Authorization (BOLA)

**진입점**

- `GET /api/v1/notifications/me?recipientId=...` — `NotificationController#list`
- `GET /api/v1/notifications/{id}` — `NotificationController#get` (현 단계 echo only)
- `PUT /api/v1/users/{recipientId}/preferences` — `UserPreferenceController#update`
- `POST /api/v1/devices` — `DeviceTokenController#register` (body 의 `recipientId`)

**현재 상태**

식별자가 모두 클라이언트 입력. JWT 가 활성화되어 있지 않아 "이 호출자가 진짜 그
recipient 인가" 를 서버에서 강제하지 못한다. 같은 사용자가 다른 사용자의
recipientId 로 호출하면 그 사용자의 알림 이력 / 선호도 / push 토큰을 조회·변경할 수
있다.

**원본 설계 의도 (README "Portfolio Set 통합")**

> 2. POST /api/v1/notifications (Bearer JWT, Idempotency-Key)
> 3. JWK Set 서명 검증 — 의도된 흐름, resource-server 의존 추가는 후속 작업

즉 auth-service 통합과 함께 closing 하기로 한 잔여물. 본 hub 가 단독으로
"누구나 호출 가능" 한 운영 노출은 가정하지 않는다 (Helm ingress 도 NetworkPolicy /
인증 게이트웨이 뒤에 있음을 전제).

**운영 이행**

1. `spring-boot-starter-oauth2-resource-server` 추가
2. `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` 를 auth-service 의 JWK Set 으로 설정
3. controller / use case 입력 `recipientId` 와 JWT subject 비교 — 다르면 403
4. 운영 자동 발송 (서비스 계정) 은 별도 scope 로 분기

본 문서가 매핑하는 시점에서는 fix 대상이 아니라 후속 ADR / 통합 시점에 한꺼번에 처리한다.

---

## API2 — Broken Authentication

**현재 상태**

- 일반 사용자 / 호출자 인증: **미구현**. README 통합 도식에서 의도된 흐름으로만 표기.
- 운영 endpoint (`/api/v1/admin/**`): 정적 token (`X-Admin-Token` 헤더). 자세히는 API5.
- vendor → 본 hub webhook: HMAC-SHA256 + 5분 replay window
  ([ADR 0014](../adr/0014-hmac-webhook-callback-verification.md)). 이건 인증의 일종이지만
  대상이 vendor 라 사용자 인증과 별개 path.

**한계**

JWT 검증 자체가 없으므로 가짜 토큰, 만료 토큰, signature 변조 모두 통과한다. 이는
저장소 README 와 SECURITY.md 에 "인증은 후속" 으로 명시되어 있다. 본 점검 문서는
현실태를 정확화한다.

**운영 이행**

1. `JwtDecoder` 빈 + JWK Set URL 주입
2. `SecurityFilterChain` 으로 path 별 권한:
   - `/api/v1/notifications/**`, `/api/v1/users/**`, `/api/v1/devices` → `authenticated()`
   - `/api/v1/admin/**` → `hasRole('ADMIN')` (token 가드는 fallback 으로 유지)
   - `/api/v1/deliveries/*/ack`, `/webhooks/**` → `permitAll()` (HMAC 가 인증)
   - `/actuator/{health,info,prometheus}` → `permitAll()` (NetworkPolicy 로 IP 제한)
3. token revocation / introspection 활성 (auth-service 가 노출)

---

## API3 — Broken Object Property Level Authorization

**진입점**

- `POST /api/v1/notifications` body — `title`, `body`, `payload` (Map<String, String>)
- `GET /api/v1/notifications/me` response — `DeliveryHistoryPage.Item`
- audit log — `Slf4jAuditLogger`

**현재 상태**

- 입력 길이 제약: title ≤ 200, body ≤ 4000, payload value 는 Mustache 렌더링 단계에서
  사용. payload key/value 자체 길이 제약은 도메인 단에서 강제 안 함.
- 응답: 이력 조회는 `id / title / kind / status / createdAt` 만 노출. body / payload 는
  미노출. raw body 는 단건 조회 endpoint 가 아직 echo 만 하므로 사실상 노출 없음
  (실제 detail use case 가 추가될 때 같이 재검토).
- audit: `NOTIFICATION_FANNED_OUT` 등에 `notificationId / kind / channels` 만 기록. raw body /
  payload 는 audit 에 넣지 않는다 — OTP / 결제 영수증 등 민감 본문이 audit 로 새지 않게
  의도된 분리.
- DB: `notification.body` / `delivery_attempt.rendered_body` 는 그대로 저장됨. 컬럼 자체
  암호화는 안 함.

**한계 / 후속**

- payload key/value 길이 제약 (현재 무제한) — Map 의 총 직렬화 길이로 가드 검토.
- 민감 채널 (SECURITY 등) 의 body 를 별도 컬럼 / 별도 테이블로 분리해 retention 단축.
- detail endpoint 추가 시 응답 필드를 use case 별로 명시 (`*` projection 금지).

---

## API4 — Unrestricted Resource Consumption

**진입점 / 가드**

1. **알림 발송 rate limit** — recipient × channel 별 token bucket.
   `RedisRateLimiter` (Lua atomic batch), 한도는 `application.yml`
   `ratelimit.{push,email,sms,kakao}-per-window`.
   - PUSH/EMAIL 분당 30, SMS/KAKAO_ALIMTALK 분당 5
   - 묶음 batch 차감 — 하나라도 한도 초과면 모두 거절 (부분 leak 방지)
   - 초과 시 `429 + Retry-After`
2. **이력 페이지네이션 cap** — `ListMyDeliveriesService` 가 `Math.min(limit, 100)` 강제.
   기본 20, 최대 100.
3. **DLQ list cap** — `DlqAdminService#list` 가 `Math.min(Math.max(limit, 1), 200)`.
4. **payload / body 크기** — `@Size(max = 4000)` body, `@Size(max = 200)` title.
5. **fan-out 폭** — Recipient.channels 에 PUSH 가 있으면 active device token 만큼 expand.
   디바이스 등록 자체에는 사용자별 상한이 없다. 이론상 한 사용자가 device token N 개를
   계속 register 하면 한 알림이 N 개 push attempt 로 분기 — rate limit 으로는
   "한 알림 = 한 PUSH demand" 로 묶이지 않고 device 별 attempt 각각 한 토큰씩 차감해
   분당 30 토큰을 device 31 개째에 소진. 즉 device 수가 시스템을 폭주시키진 않지만
   비용을 부풀린다.
6. **Kafka consumer / worker** — Resilience4j retry max 3, attempt 단위 retry max 5
   (exponential backoff + jitter).
7. **JVM / DB** — HikariCP 20 connection limit, virtual thread 사용, K8s pod resource
   request/limit (Helm `resources.*`).

**한계 / 후속**

- recipient 별 device token 수 상한 (예: 10개) — `RegisterDeviceTokenUseCase` 에서 검사.
- 같은 webhook URL 로의 ack flood — IP 단 ingress rate limit (Nginx Ingress
  `limit_req`) 검토.

---

## API5 — Broken Function Level Authorization

**진입점**

- `/api/v1/admin/dlq/**` — `AdminDlqController` (list / replay / discard)

**현재 상태**

- `AdminAuthFilter` 가 path prefix `/api/v1/admin/` 만 가드:
  - `X-Admin-Token` 헤더와 `admin.auth.token` (env `ADMIN_AUTH_TOKEN`) 을
    `MessageDigest.isEqual` 로 timing-safe 비교.
  - token 미설정 → **default-deny** (모든 admin 요청 거절).
  - admin path 가 아니면 filter 그대로 통과.
- `AdminContext` (ThreadLocal) 에 admin=true 세팅, `DlqAdminService` 의
  `requireAdmin()` 가 use case 단에서 한 번 더 확인 → 통과 못 하면
  `UnauthorizedAdminException` (`401 UNAUTHORIZED_ADMIN`).
- finally 블록에서 `AdminContext.clear()` — virtual thread 환경에서도 ThreadLocal 누수
  없음.

**한계 / 후속**

- 정적 token 1개. rotation 시 양 측 동시 교체 필요 — Spring Security + role 로 교체 시
  multi-credential 자연스럽게.
- 운영자 단위 audit 가 없음 (token 공유). role 도입 후 actor 식별자를 audit 에 기록.
- `RegisterTemplateUseCase`, `RegisterDeviceTokenUseCase` 도 운영 도메인에 가까운데
  현재는 admin path 가 아님 — auth 도입 시 함께 권한 분리.

---

## API6 — Unrestricted Access to Sensitive Business Flows

**민감 flow**

1. **알림 발송 자체** — 비용 (SMS / Kakao 알림톡) + 스팸 신고 위험.
2. **DLQ replay** — 운영자가 임의로 큰 묶음을 다시 흘려 vendor 측 비용 폭증 + 사용자
   중복 알림.
3. **template 등록** — 악의적 운영자가 phishing 본문을 등록.

**가드**

- 발송: `Idempotency-Key` 헤더 필수 + Redis SETNX TTL 24h (ADR 0004), recipient 별
  channel rate limit (ADR 0006), opt-out / DND / 알림톡 야간 차단 (ADR 0005).
- DLQ replay: admin token 가드 + audit (`DLQ_REPLAY`).
- template: 현재 anyone-can-register (auth 도입 시 admin role 로 묶음).

**한계 / 후속**

- 운영자 일괄 replay 가 rate limit 을 우회. replay 시점에는 다시 token bucket 을
  통과시키지 않음 — Outbox 가 한 번 더 publish 되면 그 후 worker dispatch 단에서
  vendor 호출이 일어나기 때문. 운영자가 한 번에 1000 건 replay 하면 1000 건 vendor
  호출 (vendor 측 throttling 만이 가드). batch replay cap 검토 필요.
- vendor abuse 모니터링 — `vendor.cost.month` metric / 채널별 실패율 dashboard.

---

## API7 — Server-Side Request Forgery (SSRF)

**검토한 표면**

- vendor webhook 콜백 target URL — **외부에서 우리 측으로 들어오는 요청**, 본 hub 가
  outbound URL 을 따라가지 않음. SSRF 표면 아님.
- vendor 호출 — `MockFcmClient` / `MockSesClient` / `MockTwilioClient` /
  `MockKakaoAlimTalkClient` 모두 실제 외부 HTTP 호출 없음 (mock). 실 SDK 로 교체 시
  vendor 호스트는 SDK 설정 / 자격 증명으로 고정 — 사용자 입력으로 URL 을 조립하는
  경로 없음.
- 이미지 / 첨부 — 본 hub 는 첨부 / 이미지 fetch 기능 없음.
- template — Mustache `{placeholder}` 만 사용. URL fetch / include directive 없음.

**검증**

```
$ grep -RIn -E '(HttpURLConnection|RestTemplate|WebClient|new URL|URI\.create|HttpClient)' \
    --include='*.java' notification-*/src/main
(매치 없음)
```

**향후 재검토 시점**

- 실 vendor SDK 로 교체할 때 — vendor 호스트가 환경 변수로 들어오는 경우 allowlist 강제.
- 이미지 / 첨부 기능 추가 시 — internal IP / metadata endpoint (`169.254.169.254`) 차단,
  redirect follow 제한.

---

## API8 — Security Misconfiguration

**Secret 관리**

- vendor webhook secret / admin token / DB credential 모두 env 평문 default
  (`application.yml`). prod profile 은 env 변수만 노출하고 실값은 외부 주입.
- Helm chart `secrets.mode=external` 권장 — ExternalSecrets / Vault / KMS 가 미리
  Secret 객체를 만들고 chart 는 `existingSecretName` 으로 envFrom.
- SECURITY.md "범위 외" 절에 "데모용 docker-compose / Helm dev 의 평문 자격 증명" 명시.

**CORS / 기본 응답**

- 현 단계 `@CrossOrigin` / `WebMvcConfigurer#addCorsMappings` 적용 없음. Spring Boot
  기본은 CORS 미허용 (= same-origin). 브라우저에서 cross-site fetch 가 차단되므로
  현 시점에는 별도 가드 불필요.
- 운영에 브라우저 UI 가 붙으면 origin allowlist 명시 (admin UI / vendor 콘솔 도메인
  한정).

**기타**

- `management.endpoint.health.show-details: when-authorized` — 무인증 호출엔
  status only 노출.
- Actuator 노출 path 는 `health / info / prometheus / metrics` 만 (`shutdown` 등 미노출).
- Helm `podSecurityContext.runAsNonRoot=true`, `readOnlyRootFilesystem=true`,
  `capabilities.drop=[ALL]`, `seccompProfile=RuntimeDefault`.
- `automountServiceAccountToken=false` (IRSA 가 아닐 때 기본 토큰 노출 차단).

**한계 / 후속**

- 헤더 보안 (`X-Content-Type-Options`, `X-Frame-Options`, `Strict-Transport-Security`) —
  Spring Security 도입과 함께 default chain 사용.
- TLS 종료 — ingress 단에서 처리, internal 트래픽 mTLS 는 service mesh 도입 시점.

---

## API9 — Improper Inventory Management

**API surface 게시**

- 코드 진입점은 모두 `/api/v1/**` prefix.
  - `/api/v1/notifications/**`
  - `/api/v1/users/{recipientId}/preferences`
  - `/api/v1/devices`
  - `/api/v1/templates`
  - `/api/v1/deliveries/{id}/ack`
  - `/api/v1/admin/dlq/**`
- Springdoc OpenAPI → `/swagger`.
- Backstage catalog 항목 `notification-rest` 에 OpenAPI snippet 게시 (`catalog-info.yaml`).
- Helm Ingress path 3분리: `/api/v1/notifications/*`, `/api/v1/admin/*`, `/webhooks/*`.

**deprecated 경로**

- 없음. 현 시점 v1 only.

**한계 / 후속**

- catalog-info.yaml 의 OpenAPI 가 실제 controller 와 100% 일치하는지 CI 가드 (Schemathesis
  / spectral) 도입 검토.

---

## API10 — Unsafe Consumption of APIs ⭐

**대상**

본 hub 는 4개 vendor (FCM / SES / Twilio / Kakao 알림톡) 의 응답을 두 경로로 신뢰한다.

1. **dispatch 응답** — `DeliveryGateway#dispatch` 가 vendor 의 message id 를 반환하면
   `DeliveryAttempt#markSucceeded(vendorMessageId)` 로 그대로 DB 에 저장.
2. **webhook ack 콜백** — `POST /api/v1/deliveries/{id}/ack` 에 vendor 가 보낸
   `vendorMessageId` / `failureReason` 을 그대로 DB 에 저장.

**현재 상태 — 핵심 위험과 가드**

| 위험 | 가드 |
|---|---|
| 가짜 콜백 (URL 만 알면 vendor 인 척) | HMAC-SHA256 + 5분 replay window (ADR 0014) |
| 잘못된 vendor 식별자 | `WebhookSecrets.secretFor(vendor)` 미등록이면 401 (fail-closed) |
| 똑같은 ack 가 두 번 도착 (vendor at-least-once) | `AcknowledgeDeliveryService` 가 `attempt.isFinal()` 검사 → IGNORED_FINAL audit |
| dispatch 결과 vendor 가 4xx 던지면 | `VendorPermanentException` (retry X), `VendorInvalidRecipientException` (token 비활성화) |
| dispatch 결과 vendor 가 5xx / IO 던지면 | `VendorTransientException` → Resilience4j retry 3회, 그래도 실패면 attempt 단위 retry 5회 |
| vendor 응답이 비정상적으로 길거나 깨진 형식 | (이번 변경) **REST DTO 길이 제약 추가** |

**이번 변경 (fix)**

`AcknowledgeDeliveryRequest` 의 `vendorMessageId` / `failureReason` 에 `@Size` 제약을
추가했다. DB 컬럼 한도와 일치시켜 vendor 가 비정상적으로 긴 문자열을 보내거나 HMAC 가
유효한 채로 누군가 부풀린 payload 를 흘리더라도 ack 호출 자체가 400 으로 빠르게 거절된다.
이전엔 ack 단계가 통과해 use case 가 호출된 뒤 JPA save 단계에서
`DataIntegrityViolationException` → 500 → `GlobalExceptionHandler#handleUnexpected` 로
떨어지면서 attempt 가 final 상태로 마킹되지 못한 채 retry / EXHAUSTED 가 어긋났다.

- DTO: `notification-adapter-in/src/main/java/com/example/notification/adapter/in/rest/dto/AcknowledgeDeliveryRequest.java`
- DB 컬럼:
  - `delivery_attempt.vendor_message_id VARCHAR(128)` (Flyway V1)
  - `delivery_attempt.failure_reason VARCHAR(512)`
- 매핑된 도메인: `DeliveryAttempt#markSucceeded(String vendorMessageId)`,
  `#markFailed(String reason)`

400 응답은 `GlobalExceptionHandler` 의
`@ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, ...})`
로 일관 처리.

**dispatch 측 (mock vendor) — 현황**

`MockFcmClient` / `MockSesClient` 등 mock 4종은 vendor message id 를 우리가 생성하므로
포맷 / 길이 모두 결정적 (`projects/notification-hub/messages/{uuid}`,
`<{uuid}@email.amazonses.com>`, `SM` + 32 hex, `KKO-{uuid}`). 실 SDK 로 교체 시 SDK 가
strong-typed 응답을 주므로 본 hub 단에서 별도 가드 불필요. 다만 `DeliveryAttempt` 가
unbounded `String` 을 받으므로 도메인 단에서도 길이 제약을 두는 것을 후속 ADR 로 검토.

**failure mode 매트릭스**

| vendor 응답 | 본 hub 처리 |
|---|---|
| 200 + message id (정상) | SUCCEEDED + vendorMessageId 저장 |
| 4xx (INVALID_ARGUMENT 등) | `VendorPermanentException` → 즉시 EXHAUSTED 분기 (retry X) |
| 4xx (NOT_REGISTERED 등) | `VendorInvalidRecipientException` → EXHAUSTED + device token deactivate |
| 5xx / Throttling | `VendorTransientException` → Resilience4j retry 3회 + attempt retry |
| IO / socket reset | `UncheckedIOException` → 위와 동일 retry |
| 200 + 비정상 본문 (긴 message id, 깨진 JSON) | (이번 변경 후) ack 단계 400, 도메인 / DB 손상 X |
| 늦은 ack (이미 attempt final) | IGNORED_FINAL audit, 상태 변경 없음 |
| 같은 ack 중복 도착 | 위와 동일 |

---

## 변경 요약 (본 점검 결과)

| 항목 | 변경 | 위치 |
|---|---|---|
| API10 vendor 응답 길이 가드 | `AcknowledgeDeliveryRequest` 에 `@Size(max=128/512)` 추가 | `notification-adapter-in/.../AcknowledgeDeliveryRequest.java` |

나머지 항목은 fix 가 아닌 "후속 작업" 으로 본 문서에 명문화 — 운영 전환 시 함께 처리한다.

## 참고

- README "Portfolio Set 통합" 절 — JWT / auth-service 통합 후속 흐름.
- SECURITY.md — 본 저장소의 학습 / 운영 분리 정책.
- [ADR 0014](../adr/0014-hmac-webhook-callback-verification.md) — webhook HMAC.
- [ADR 0012](../adr/0012-dlq-admin-endpoint.md) — admin endpoint 가드.
- [ADR 0006](../adr/0006-rate-limit-token-bucket.md) — rate limit.
- [ADR 0004](../adr/0004-idempotency-outbox-retry.md) — Idempotency + Outbox + retry.
