# ADR-0014: HMAC 기반 webhook 콜백 서명 검증

## 상태
적용

## 배경

vendor (FCM / SES / Twilio / 카카오 알림톡) 는 메시지 전송 결과를 우리 시스템에
*콜백 (webhook)* 으로 알려준다. 우리는 그 콜백을 받아 `DeliveryAttempt` 의 status 를
`DELIVERED` / `BOUNCED` / `FAILED` 등으로 마킹한다.

문제: 콜백 URL 은 외부에 노출되는 HTTP endpoint 라 *누구나* 호출할 수 있다. 누군가
URL 만 알아내고 가짜 콜백을 보내면:

- 사용자에게 알림이 *실제로는 안 갔는데* 시스템은 `DELIVERED` 로 마킹 → 사용자는 "왜 알림
  안 와요" 컴플레인, 시스템 로그는 "정상 전송" 이라 디버깅 misdirection
- 또는 정상 전송된 알림을 `FAILED` 로 마킹시켜 우리 retry 가 무한 발생 → vendor 비용 폭증

운영 표준은 *모든 콜백 요청에 vendor 와 우리만 아는 secret 으로 만든 HMAC 서명* 을 첨부하고,
서버가 같은 secret 으로 서명을 다시 계산해 비교한다. Stripe / GitHub / 토스페이먼츠 / 카카오
모두 같은 패턴.

## 결정

### 서명 형식

요청에 두 헤더 추가:

```
X-Notification-Hub-Vendor:    fcm
X-Notification-Hub-Signature: v1=<hex(HMAC-SHA256(secret, "{timestamp}.{body}"))>
X-Notification-Hub-Timestamp: <epoch-millis>
```

`v1=` prefix 는 후속 알고리즘 변경 (예: SHA-512 / Ed25519) 대비. timestamp 는 body 와 함께
HMAC 입력에 들어가서 *같은 body 다른 timestamp* 의 서명이 다름 → replay 차단의 1차 신호.

### 검증 단계

1. **vendor 식별** → `WebhookSecrets.secretFor(vendor)` 로 secret 조회. 미등록이면 401
   (fail-closed — secret 없으면 *어떤 요청도 통과 X*)
2. **timestamp 윈도우 검사** → `|now - timestamp| > 5분` 이면 401. 가짜 콜백이 *예전에 한 번*
   유효했던 서명을 그대로 재전송하는 replay attack 차단
3. **HMAC 비교** → 같은 secret + timestamp + body 로 다시 계산. `MessageDigest.isEqual` 로
   timing-safe 비교 (단순 `String.equals` 는 비교 시간이 일치 길이에 비례해 *부분 일치 정도*
   를 timing 으로 누설 — side channel)

### 왜 5분 윈도우

- 너무 좁으면 vendor 측 retry 지연 / 우리 시계 오차로 정상 콜백도 거절
- 너무 넓으면 가짜 콜백이 *오래된 유효 서명* 을 재사용할 수 있는 시간 길어짐
- Stripe / GitHub 모두 5분 — 업계 표준

### vendor 별 별도 secret

한 vendor 의 secret 이 유출돼도 다른 vendor 에는 영향 없도록 격리. `WebhookSecrets` 가 yml
의 `webhook.secrets.{vendor}` 매핑을 들고 있고, 각 secret 은 환경변수 / KMS 로 주입.

### secret 미설정 시 정책

`fail-closed` — secret 미설정 vendor 의 콜백은 *모두* 401. dev 환경에서도 명시 secret 있어야
testing 가능. 운영에서 새 vendor 추가 시 secret 등록을 잊으면 콜백이 그대로 막혀 알게 됨
(silent fail 보다 나음).

## 대안

### IP allowlist
탈락 — vendor 의 IP 범위는 자주 바뀌고 (CDN), allowlist 갱신 누락 시 사고. HMAC 은 secret
만 관리하면 IP 변화에 무관.

### mTLS
검토 — 더 강한 보안이지만 vendor 별 인증서 / 인증서 갱신 운영 비용. 본 ADR 의 secret 만으로
충분. 금융 등 더 높은 수준 필요 시 후속.

### 단순 secret 헤더 (서명 X)
탈락 — secret 자체가 매 요청에 평문 전송돼 네트워크 도청 시 즉시 노출. HMAC 은 secret 가
*절대* 네트워크에 안 나감 (서명만 나감).

## 결과

- 가짜 콜백 차단 — secret 없으면 절대 통과 못 함
- replay 차단 — 5분 윈도우 + timestamp 가 HMAC 입력에 포함
- vendor 격리 — 한 secret 유출이 다른 vendor 콜백에 영향 X
- (단점) secret 갱신 (rotation) 운영 절차 필요 — 본 ADR 범위 밖, 후속 ADR 에서 grace
  window 패턴 (billing-platform 의 ADR-0029 webhook secret rotation 참고)
- (단점) body 직렬화 일관성 — 본 ADR 에서는 controller 에서 다시 직렬화. 운영에서는
  `ContentCachingRequestWrapper` 로 raw bytes 그대로 사용하는 편이 더 정확. 포폴 단계 단순화.

## 후속

- ADR (예정): vendor secret rotation + grace window (billing-platform ADR-0029 패턴)
- ADR (예정): mTLS 도입 — 금융 / KYC 통합 시
