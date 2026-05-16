package com.example.notification.application.port.out

/**
 * vendor 측 영구 실패를 application 계층에 알리는 마커 인터페이스. 구현 클래스 (adapter-out 의
 * vendor 별 예외) 는 [DeliveryGateway.dispatch] 에서 throw 한다.
 *
 * 이 마커가 붙은 예외는 retry 무의미 (invalid token, malformed payload 등) — Resilience4j
 * 단계에선 ignore-exception, application 단계에선 즉시 markFailed + (PUSH 라면) device token
 * 비활성화로 간다.
 *
 * 이 마커가 없는 RuntimeException 은 일시 오류로 간주 — Resilience4j retry 후에도
 * 실패하면 도메인 단의 retry 카운트로 흡수.
 *
 * **왜 마커 인터페이스**: application 모듈은 adapter-out 의 구체 예외 클래스에 의존할 수
 * 없다 (헥사고날 의존 방향). 그렇다고 클래스 simple name 문자열 매칭으로 분기하면 rename
 * 한 줄로 분류가 망가지고 IDE refactor 도 못 잡는다. application 이 정의한 contract 인터페이스를
 * adapter-out 측이 implement 하는 형태가 의존 방향과 안전성 모두 만족.
 */
interface PermanentDeliveryFailure
