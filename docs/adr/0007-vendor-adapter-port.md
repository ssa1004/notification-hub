# ADR-0007: Vendor adapter abstraction — 공통 port 1개

## 상태
적용

## 배경
실제 운영에서 알림 hub 는 채널별로 vendor SDK 를 직접 호출:

- **PUSH** — Firebase Admin SDK (FCM)
- **EMAIL** — AWS SDK (SES) 또는 SendGrid
- **SMS** — Twilio Helper Library 또는 카카오 SMS SDK
- **KAKAO_ALIMTALK** — 카카오 비즈메시지 (자체 HTTP API + 인증서)

각 vendor 의 동기/비동기 / 인증 / 응답 형식 / retry 정책이 모두 다릅니다. 이를 use case 가
직접 알면 새 vendor 추가가 hub 본질 코드에 영향.

## 결정
`DeliveryGateway` 라는 공통 out-port 인터페이스 1개:

```kotlin
interface DeliveryGateway {
    fun channelType(): ChannelType
    fun dispatch(attempt: DeliveryAttempt): String  // 성공 시 vendor message id 반환
}
```

각 vendor 별 adapter 가 구현체:
- `MockFcmClient : DeliveryGateway` (channelType = PUSH)
- `MockSesClient` (EMAIL), `MockTwilioClient` (SMS), `MockKakaoAlimTalkClient` (KAKAO_ALIMTALK)

라우팅은 `DispatchDeliveryService` 가 생성자에서 모든 `DeliveryGateway` 구현체를 모아
`Map<ChannelType, DeliveryGateway>` 로 indexing. 같은 channelType 의 gateway 가 두 개면 즉시
`IllegalStateException` 으로 fail-fast (config drift 방지).

vendor 호출의 retry / circuit breaker 는 adapter 단의 `@Retry(name="vendorFcm")` 로 처리 —
도메인이 알 필요 없음. config 는 `application.yml` 의 `resilience4j.retry.instances` 에 vendor
별 분리.

## 결과
- 새 vendor 추가 = adapter-out 모듈에 `@Component class FooClient : DeliveryGateway`
  1개 + Resilience4j config 1줄 + ratelimit config 1줄. 도메인 / application 변경 0
- vendor SDK 의존성이 adapter-out 의 한 클래스에만 격리 → version upgrade 영향 범위 작음
- vendor 호출 단위 테스트는 SDK mock + Gateway interface 만으로 가능
- (현재 상태) 학습 목적이라 vendor SDK 추가 X — Mock 4종으로 대체. 실제 채택 시엔 SDK 가 한
  vendor 당 5~50MB 추가됨
- (단점) vendor 의 비동기 콜백 (vendor → 우리 webhook) 은 별도 `AcknowledgeDeliveryUseCase`
  로 처리 — DeliveryGateway 추상은 동기 호출만 책임. 콜백 형식이 vendor 별로 매우 다르므로
  adapter-in 의 controller 가 vendor 별 분리

## 다시 검토할 시점
- 동일 채널 내 vendor 다중화 (PUSH 를 FCM + APNs 동시 / 자체 push) 가 필요하면 라우팅 로직을
  enum 키 외 전략 객체로 확장 (예: device 의 OS 보고 결정)
- 비동기 호출 중심 (vendor 에 비동기 send + 응답 callback) 으로 가야 하면 dispatch 시그니처를
  `CompletableFuture<String>` 으로 변경

## 용어 풀이 (쉽게)

- **out-port (아웃 포트)** — 핵심 로직이 바깥(vendor)을 부를 때 쓰는 '콘센트' 인터페이스. 안쪽은 콘센트 모양만 알고, 어느 vendor 플러그가 꽂히는지는 몰라도 된다.
- **fail-fast (빠른 실패)** — 잘못된 설정을 발견하면 조용히 넘어가지 않고 즉시 멈춰 알리는 것. 같은 채널 gateway가 둘이면 바로 에러를 던져 운영 중 사고를 예방한다.
- **config drift (설정 어긋남)** — 의도와 다르게 설정이 슬그머니 틀어진 상태(예: 같은 채널에 vendor가 둘 등록됨). fail-fast로 이런 어긋남을 부팅 때 잡는다.
- **서킷 브레이커 (Circuit Breaker)** — vendor 호출이 자꾸 실패하면 두꺼비집처럼 잠시 회선을 끊어 즉시 실패시키고, 죽은 서버를 계속 두드리는 걸 막는 것.
- **vendor SDK** — FCM·SES·Twilio 같은 외부 업체가 제공하는 호출용 라이브러리. 무겁고 업체마다 사용법이 달라, 한 클래스 안에만 가둔다.
