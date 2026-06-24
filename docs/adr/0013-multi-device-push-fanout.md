# ADR-0013: Multi-device push fan-out + 영구 실패 자동 비활성화

## 상태
적용

## 배경
초기 구현은 한 사용자의 PUSH 채널을 1개만 사용 — `Recipient.channels` 의 placeholder PUSH
주소 하나만. 실제로는:

- 한 사용자가 핸드폰 + 태블릿 + 웹 등 여러 device 를 보유. OTP 같은 보안 알림은 모든 device
  에 전송되어야 사용자가 어디서든 받을 수 있음.
- FCM / APNs 가 발급한 token 은 OS 가 종종 회전 — 옛 token 으로 보내면 vendor 가
  `NOT_REGISTERED` (FCM) 또는 `Unregistered` (APNs) 응답. 다음 발송에서도 같은 실패 반복.

`RegisterDeviceTokenUseCase` 는 이미 한 사용자에 여러 device token 등록을 허용. 데이터
모델은 준비됨 — 사용 안 하던 것뿐.

## 결정
### 1. PUSH 채널을 active device 별로 fan-out
`SendNotificationService.expandPushFanOut()` — `ChannelResolver` 가 반환한 채널 리스트에
PUSH 가 있으면, `DeviceTokenRepository.findActiveByRecipientId()` 로 N 개 active device 를
가져와 N 개의 PUSH `Channel` 로 치환.

```
ChannelResolver 결과 : [PUSH(placeholder), EMAIL, SMS]
expandPushFanOut 후  : [PUSH(token-A), PUSH(token-B), PUSH(token-C), EMAIL, SMS]
   ↓
DeliveryAttempt × 5 생성, Outbox 에 5개 DeliveryRequested 발행
   ↓
notification.delivery.push topic 에 3개 메시지 → 3개의 worker 호출
notification.delivery.email topic 에 1개
notification.delivery.sms topic 에 1개
```

active device 가 0 개면 PUSH 채널 자체를 결과에서 제거 → push 발송 시도 안 함 (사용자가
device 등록 안 한 상태).

### 2. 영구 실패 시 device token 자동 비활성화
`DispatchDeliveryService` 가 vendor 의 `VendorPermanentException` (FCM `NOT_REGISTERED`,
APNs `Unregistered` 등) 을 catch 하면:
- 도메인은 `markFailed("permanent: ...")` 처리 — 5회 retry 거치지만 모두 fail
- 추가로 PUSH 채널이면 `DeviceTokenRepository.deactivateByToken(address)` 호출 →
  `disabledAt` 마킹 → 다음 fan-out 에서 자동 제외

비활성화 자체가 실패해도 dispatch 결과에는 영향 없음 (catch + log) — 다음 영구 실패에서 재시도.

### 3. raw token 식별
`Channel.address` 가 raw FCM token 이라 `deactivateByToken(token)` 가 직접 매칭. 한 사용자가
같은 token 을 두 device 에 가질 수 없는 unique 제약 (V1__init.sql) 때문에 1:1 매핑.

## 결과
- **사용자가 모든 device 에서 알림 받음** — 핸드폰 잠금이어도 태블릿 / 웹에서 OTP 확인 가능.
- **stale token 자동 정리** — vendor 가 `NOT_REGISTERED` 응답하는 순간 다음 알림부터 제외.
  사용자가 앱 재설치 → 새 token 등록으로 자연스럽게 갱신.
- **주변 채널 (EMAIL/SMS) 무영향** — fan-out 확장은 PUSH 만. 다른 채널은 그대로 1:1.
- **rate limit 도 device 단위가 아닌 채널 단위 유지** — push 30/min 한도는 PUSH 채널 전체
  (모든 device 합산). 하나의 vendor (FCM) 가 한 사용자에 30개를 받든 1개씩 N device 받든
  vendor 측 부하 같음.
- (단점) device 가 많으면 한 알림이 N개의 attempt 로 → DB row N 배 + Kafka 메시지 N 배. 보통
  한 사용자 1~3 device 라 영향 작지만, 잘못 등록된 token 이 누적되면 bloat.
- (단점) `deactivateByToken` 이 dispatch 트랜잭션 안에서 실행 — DB lock 경합 가능. 비활성화는
  후행 트랜잭션 / async event 로 분리 가능 (현재는 단순화).

## 다시 검토할 시점
- "내 device 별 read 상태" 같은 추가 요구가 생기면 `DeliveryAttempt` 에 `deviceId` FK 추가 +
  device 모델과 JOIN.
- Web Push (Service Worker) 추가 시 `Platform.WEB` 이 vendor (FCM web push, OneSignal 등) 와
  매핑되도록 `DeliveryGateway` 분기 — 현재 PUSH 가 PUSH 라는 단일 ChannelType 인 건 유지.
- 100+ device 사용자 등 비정상 케이스 — 등록 시 사용자당 device 수 상한 + 가장 오래된 것부터
  자동 retire.

## 용어 풀이 (쉽게)

- **multi-device fan-out** — 한 사용자의 핸드폰·태블릿·웹 등 켜져 있는 모든 기기로 같은 푸시를 동시에 펼쳐 보내는 것. 어느 기기를 보고 있든 OTP를 받게 한다.
- **token / token rotation (토큰 회전)** — token은 푸시를 보낼 기기의 '주소표'. OS가 이따금 이 주소표를 새로 발급(rotation)하므로 옛 주소로 보내면 실패한다.
- **stale token / NOT_REGISTERED (만료된 토큰)** — 더 이상 유효하지 않은 옛 주소표. vendor가 `NOT_REGISTERED`로 거절하면 다음부터 그 기기는 자동으로 발송 대상에서 뺀다.
- **bloat (불필요한 비대)** — 잘못된 토큰·죽은 기기 기록이 자꾸 쌓여 DB와 메시지가 쓸데없이 불어나는 것.
- **Web Push / Service Worker** — 앱이 아니라 웹 브라우저로 푸시를 보내는 기술. Service Worker는 브라우저 뒤에서 알림을 대신 받아주는 작은 백그라운드 프로그램.
- **FK (Foreign Key, 외래 키)** — 한 테이블의 줄이 다른 테이블의 어느 줄을 가리키는지 묶어주는 연결 고리. 알림과 기기 정보를 이어 붙일 때 쓴다.
