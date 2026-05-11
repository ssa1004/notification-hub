# ADR-0010: Graceful shutdown — Spring + K8s 연계

## 상태
적용

## 배경
SIGTERM 을 즉시 SIGKILL 로 바꿔버리면:

- **REST 응답 중간 끊김** — `POST /notifications` 가 DB commit 직후 / Outbox INSERT 전에
  끊기면 idempotency 키만 점유된 채 알림 실종.
- **Outbox row 가 PUBLISHED 마킹 직전에 끊김** — outbox row 는 다음 polling 에서 재발행되어
  최종적으로 멱등 처리 가능 (consumer 측 dedupe 가 있다면). 다만 PUBLISHED 마킹과 Kafka send
  사이에 끊기면 중복 발행 가능 — at-least-once 의 본질.
- **Kafka consumer offset commit 직전에 끊김** — 같은 메시지를 다시 처리. consumer 측 멱등성
  필요.

## 결정
3단 graceful shutdown.

### 1. Spring Boot 측
```yaml
server.shutdown: graceful
spring.lifecycle.timeout-per-shutdown-phase: 25s
```
- `server.shutdown=graceful` — embedded Tomcat 이 SIGTERM 후 신규 connection 거절, in-flight
  요청은 완료 대기. timeout 안에 안 끝나면 강제 종료.
- `timeout-per-shutdown-phase: 25s` — Spring 의 SmartLifecycle phase 별 정리 시간.
  `WebServerStartStopLifecycle`, scheduled task, Kafka listener container 가 이 시간 안에
  graceful stop.

### 2. K8s preStop
```yaml
lifecycle:
  preStop:
    exec:
      command: ["sh", "-c", "sleep 5"]
```
- 5초 sleep — kube-proxy 의 iptables update + LB endpoint sync 가 SIGTERM 보다 늦게
  도착하는 race 회피. 이 5초 동안 service endpoint 에서 pod 가 빠지면서, 신규 트래픽이
  다른 pod 로 라우팅됨.

### 3. K8s grace period
```yaml
terminationGracePeriodSeconds: 30
```
- preStop 5s + Spring graceful 25s = 합 30s. 이 시간 안에 SIGKILL 안 함.

### 시퀀스
1. K8s 가 pod 종료 결정 (deploy 새 버전, scale down 등)
2. K8s 가 service endpoint 에서 pod 제거 (LB drain 시작)
3. preStop 의 `sleep 5` 실행 — endpoint 빠지는 시간 확보 (process 는 아직 살아있음)
4. SIGTERM → Spring graceful shutdown 시작
5. embedded Tomcat 이 신규 connection 거절, in-flight 요청 완료 대기 (max 25s)
6. Scheduled task / Kafka listener / OutboxRelay graceful stop
7. JVM 종료 (성공) 또는 30s 도달 시 SIGKILL

## 결과
- **REST 응답 손실 zero** — in-flight 요청은 끝까지 처리 후 종료. 신규 요청은 다른 pod 로.
- **Outbox 일관성 유지** — outbox 는 polling 기반이라 어느 pod 에서 죽어도 다음 polling 에서
  복구. 단 같은 row 가 발행 후 markPublished 사이에 죽으면 중복 발행 가능 — Kafka consumer
  의 멱등성 (이미 처리한 deliveryAttemptId 무시) 으로 대응.
- **rolling deploy 무중단** — `maxSurge: 1, maxUnavailable: 0` 와 함께 새 pod 가 ready 된
  후에만 옛 pod 종료 → 503 zero.
- (단점) graceful 30s + maxSurge:1 → 2 pod 환경에서 한 deploy 에 1분 정도 소요 — 수십 pod
  규모면 deploy 가 길어짐. surge 비율 늘리거나 grace 줄여야 함.
- (단점) Kafka consumer 가 in-flight 메시지 처리 중 25s 안에 못 끝내면 강제 종료 → offset
  미커밋 → 재처리. 메시지 처리에 25s 넘는 작업이 있으면 chunk 쪼개야 함.

## 다시 검토할 시점
- 한 요청이 25s 를 넘는 케이스 (대용량 fan-out, vendor RTT 가 매우 긴 채널) 가 생기면
  `timeout-per-shutdown-phase` 와 `terminationGracePeriodSeconds` 같이 늘림.
- spot instance / preemptible VM 으로 가면 grace 30s 도 보장 안 될 수 있음 → Pod 가 죽어도
  처리되도록 outbox / kafka offset 을 100% 신뢰해야 함 (현재 설계와 부합).
