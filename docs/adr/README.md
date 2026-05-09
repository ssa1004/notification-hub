# Architecture Decision Records

설계 의사결정의 배경 + 결정 + 결과를 한 파일당 하나로 보관합니다. 5년차 개발자가 cold-read
30초 안에 핵심을 잡을 수 있는 분량 (1~2페이지) 을 목표로 합니다.

## 목록

- [ADR-0001 Hexagonal architecture + 멀티모듈 분리](0001-hexagonal-architecture.md)
- [ADR-0002 다채널 fan-out — Kafka topic 분리](0002-channel-fanout-topics.md)
- [ADR-0003 템플릿 엔진 — Mustache 선택](0003-template-engine-mustache.md)
- [ADR-0004 Idempotency-Key + Outbox + retry 의 3중 안전망](0004-idempotency-outbox-retry.md)
- [ADR-0005 사용자 선호도 — 채널 우선순위 / opt-out / 방해금지](0005-user-preference-priority.md)
- [ADR-0006 Rate limit — 사용자당 채널별 token bucket](0006-rate-limit-token-bucket.md)
- [ADR-0007 Vendor adapter abstraction — 공통 port 1개](0007-vendor-adapter-port.md)
- [ADR-0008 HikariCP 명시 튜닝 + leak detection](0008-hikaricp-tuning.md)
- [ADR-0009 K8s 3종 probe (startup/readiness/liveness)](0009-k8s-probes.md)
- [ADR-0010 Graceful shutdown — Spring + K8s 연계](0010-graceful-shutdown.md)
- [ADR-0011 Resilience4j retry — exp backoff + jitter, vendor 별 분리](0011-resilience4j-retry-tuning.md)
- [ADR-0012 DLQ 운영 endpoint — list / replay / discard](0012-dlq-admin-endpoint.md)
- [ADR-0013 Multi-device push fan-out + 영구 실패 자동 비활성화](0013-multi-device-push-fanout.md)

## 작성 양식

```
# ADR-NNNN: <짧은 제목>

## 상태
적용 / 폐기 / 대체

## 배경
무엇을 풀어야 하는지. 다른 옵션은 뭐가 있었는지.

## 결정
무엇을 정했는지.

## 결과
이 결정이 만든 trade-off. 단점도 명시.

## 다시 검토할 시점
어떤 조건이 바뀌면 재논의해야 하는지.
```
