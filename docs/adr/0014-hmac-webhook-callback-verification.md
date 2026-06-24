# ADR-0014: HMAC 기반 webhook 콜백 서명 검증

## 상태
적용

## 배경

vendor (FCM / SES / Twilio / 카카오 알림톡) 는 메시지 전송 결과를 우리 시스템에
콜백 (webhook) 으로 알려준다. 우리는 그 콜백을 받아 `DeliveryAttempt` 의 status 를
`DELIVERED` / `BOUNCED` / `FAILED` 등으로 마킹한다.

문제: 콜백 URL 은 외부에 노출되는 HTTP endpoint 라 누구나 호출할 수 있다. 누군가
URL 만 알아내고 가짜 콜백을 보내면:

- 사용자에게 알림이 실제로는 안 갔는데 시스템은 `DELIVERED` 로 마킹 → 사용자는 "왜 알림
  안 와요" 컴플레인, 시스템 로그는 "정상 전송" 이라 디버깅 방향이 어긋남
- 또는 정상 전송된 알림을 `FAILED` 로 마킹시켜 우리 retry 가 무한 발생 → vendor 비용 폭증

해결책은 모든 콜백 요청에 vendor 와 우리만 아는 secret 으로 만든 HMAC 서명을 첨부하고,
서버가 같은 secret 으로 서명을 다시 계산해 비교하는 방식이다. webhook 을 운영하는 대부분의
서비스에서 쓰는 일반적인 패턴이다.

## 결정

### 서명 형식

요청에 두 헤더 추가:

```
X-Notification-Hub-Vendor:    fcm
X-Notification-Hub-Signature: v1=<hex(HMAC-SHA256(secret, "{timestamp}.{body}"))>
X-Notification-Hub-Timestamp: <epoch-millis>
```

`v1=` prefix 는 후속 알고리즘 변경 (예: SHA-512 / Ed25519) 대비. timestamp 는 body 와 함께
HMAC 입력에 들어가서 같은 body 라도 timestamp 가 다르면 서명이 달라짐 → replay 차단의 1차
신호.

### 검증 단계

1. **vendor 식별** → `WebhookSecrets.secretFor(vendor)` 로 secret 조회. 미등록이면 401
   (fail-closed — secret 없으면 어떤 요청도 통과시키지 않음)
2. **timestamp 윈도우 검사** → `|now - timestamp| > 5분` 이면 401. 가짜 콜백이 예전에 한 번
   유효했던 서명을 그대로 재전송하는 replay attack 차단
3. **HMAC 비교** → 같은 secret + timestamp + body 로 다시 계산. `MessageDigest.isEqual` 로
   timing-safe 비교 (단순 `String.equals` 는 비교 시간이 일치 길이에 비례해 부분 일치 정도를
   timing 으로 누설 — side channel)

### 왜 5분 윈도우

- 너무 좁으면 vendor 측 retry 지연 / 우리 시계 오차로 정상 콜백도 거절
- 너무 넓으면 가짜 콜백이 오래된 유효 서명을 재사용할 수 있는 시간이 길어짐
- 일반적인 webhook 서명 사양에서 5분 전후를 권장 윈도우로 둠

### vendor 별 별도 secret

한 vendor 의 secret 이 유출돼도 다른 vendor 에는 영향 없도록 격리. `WebhookSecrets` 가 yml
의 `webhook.secrets.{vendor}` 매핑을 들고 있고, 각 secret 은 환경변수 / KMS 로 주입.

### secret 미설정 시 정책

`fail-closed` — secret 미설정 vendor 의 콜백은 모두 401. dev 환경에서도 secret 이 명시되어
있어야 테스트 가능. 운영에서 새 vendor 추가 시 secret 등록을 잊으면 콜백이 그대로 막혀 바로
드러남 (silent fail 보다 나음).

## 대안

### IP allowlist
탈락 — vendor 의 IP 범위는 자주 바뀌고 (CDN), allowlist 갱신 누락 시 사고. HMAC 은 secret
만 관리하면 IP 변화에 무관.

### mTLS
검토 — 더 강한 보안이지만 vendor 별 인증서 / 인증서 갱신 운영 비용. 본 ADR 의 secret 만으로
충분. 금융 등 더 높은 수준 필요 시 후속.

### 단순 secret 헤더 (서명 X)
탈락 — secret 자체가 매 요청에 평문 전송돼 네트워크 도청 시 즉시 노출. HMAC 은 secret 이
네트워크로 나가지 않음 (서명만 나감).

## 결과

- 가짜 콜백 차단 — secret 없으면 통과 불가
- replay 차단 — 5분 윈도우 + timestamp 가 HMAC 입력에 포함
- vendor 격리 — 한 secret 유출이 다른 vendor 콜백에 영향을 주지 않음
- (단점) secret 갱신 (rotation) 운영 절차 필요 — 본 ADR 범위 밖, 후속 ADR 에서 grace
  window 패턴으로 다룰 예정
- (단점) body 직렬화 일관성 — 본 ADR 에서는 controller 에서 다시 직렬화. 운영에서는
  `ContentCachingRequestWrapper` 로 raw bytes 를 그대로 사용하는 편이 더 정확. 본 단계에서는
  단순화.

## 후속

- ADR (예정): vendor secret rotation + grace window
- ADR (예정): mTLS 도입 — 금융 / KYC 통합 시

## 용어 풀이 (쉽게)

- **webhook (웹훅)** — vendor가 "전송 결과 나왔어요" 하고 우리 쪽 주소(URL)로 먼저 알려주는 콜백. 우리가 물어보는 게 아니라 저쪽이 일이 끝나면 알아서 두드린다.
- **HMAC 서명 (Hash-based MAC)** — vendor와 우리만 아는 비밀(secret)로 메시지에 찍는 '봉인 도장'. 받은 쪽이 같은 비밀로 도장을 다시 찍어보고 맞으면 진짜, 다르면 가짜로 판별한다.
- **secret (시크릿)** — vendor와 우리만 공유하는 비밀 열쇠 문자열. HMAC은 이 열쇠 자체는 네트워크로 보내지 않고 '도장 결과'만 보낸다.
- **replay attack (재전송 공격)** — 예전에 한 번 통했던 진짜 요청을 가로채 그대로 다시 보내 시스템을 속이는 공격. 5분 시간 창과 timestamp로 막는다.
- **timing-safe 비교 / side channel (타이밍 안전 비교·부채널)** — 도장을 맞춰볼 때 걸리는 시간을 늘 똑같이 맞춰, 응답 속도 차이로 정답을 한 글자씩 알아내는 새는 틈(side channel)을 없애는 것.
- **fail-closed (고장 시 잠금)** — secret이 없거나 검증이 애매하면 '일단 막는' 안전 우선 정책. 반대로 '고장 나면 통과'는 fail-open이라 부른다.
- **IP allowlist / mTLS (대안 보안)** — IP allowlist는 정해진 IP만 통과시키는 명단(주소가 자주 바뀌어 탈락), mTLS는 양쪽이 서로 인증서로 신원을 증명하는 더 강한 방식(운영 비용이 큼).
- **secret rotation (시크릿 교체)** — 유출 위험에 대비해 비밀 열쇠를 주기적으로 새것으로 갈아끼우는 운영. 옛 열쇠도 잠깐 같이 받아주는 유예(grace window)가 필요하다.
