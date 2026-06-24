# ADR-0001: Hexagonal architecture + 멀티모듈 분리

## 상태
적용

## 배경
알림 hub 는 다양한 입출력을 다룹니다 — REST 요청, Kafka consume, vendor 호출 (FCM/SES/
Twilio/Kakao), DB, Redis, audit sink. 서비스 본질은 발송 결정 흐름인데 외부 시스템에
강하게 결합하면:

- vendor SDK 가 도메인 코드에 들어와 단위 테스트가 vendor mock 없이는 불가
- 새 vendor 추가 시 도메인까지 바뀜 (open/closed 위반)
- DB 종류 (Postgres ↔ H2) 가 도메인 invariants 검증을 망침

## 결정
헥사고날 (ports & adapters) 아키텍처를 멀티모듈로 강제합니다.

```
notification-domain          (외부 의존 0 — 순수 도메인)
notification-application     (use case + Port 인터페이스, Spring stereotype 만 허용)
notification-adapter-in      (REST controller, Kafka consumer)
notification-adapter-out     (JPA, Redis, Kafka producer, Vendor adapter)
notification-bootstrap       (Spring Boot main + 통합 config + Flyway)
e2e-tests                    (Testcontainers 통합 테스트)
```

의존 방향: `domain ← application ← adapter-* ← bootstrap`. domain 은 어떤 라이브러리도
import 하지 않으며 (jakarta.validation 만 예외 — 표준), application 도 Spring framework 의
context/tx/slf4j 외엔 직접 의존 금지. JPA/Redis/Kafka SDK 는 adapter-out 에만 등장.

## 결과
- 도메인 단위 테스트가 vendor / DB / Redis 없이 mock 만으로 가능 (현재 39개 통과 4초)
- 새 vendor 추가 = adapter-out 에 `DeliveryGateway` 구현체 1개 + Resilience4j retry 만 등록.
  도메인 / application 변경 0
- adapter-in 이 adapter-out 을 직접 참조 못하게 의도적으로 분리 — Kafka consumer 가 vendor
  호출을 직접 하지 않고 application 의 use case (DispatchDeliveryUseCase) 를 거침
- (단점) 모듈 수가 많아 진입 비용. ADR 와 모듈별 build.gradle.kts 주석으로 보완
- (단점) Mapper layer (Entity ↔ Domain) 가 늘어나 boilerplate. 자동 생성 (MapStruct)
  도입은 의존 추가 부담 vs 명시성 장단점 — 일단 수기 mapper 유지

## 용어 풀이 (쉽게)

- **헥사고날 아키텍처 / port·adapter (ports & adapters)** — 핵심 로직을 가운데 두고 DB·Kafka·웹은 콘센트(port)와 플러그(adapter)로만 연결해, 바깥을 바꿔도 안쪽 코드는 안 건드리는 구조.
- **port (포트, in-port/out-port)** — 핵심이 바깥과 약속한 '콘센트' 인터페이스. 안쪽은 콘센트 모양만 알고, 거기 꽂는 실제 장치(DB·vendor)는 몰라도 된다.
- **adapter (어댑터)** — 그 콘센트에 실제로 꽂는 플러그. JPA·Redis·vendor SDK 같은 진짜 바깥 기술을 여기에 가둔다.
- **open/closed (개방-폐쇄 원칙)** — 새 기능(새 vendor)은 코드를 '추가'해서 넣고, 잘 돌던 기존 코드는 '고치지 않는다'는 설계 원칙.
- **도메인 invariant (불변식)** — 어떤 일이 있어도 항상 지켜져야 하는 규칙(예: 발송 상태는 정해진 순서로만 바뀐다). 외부 기술이 끼면 이 검증이 흐려진다.
