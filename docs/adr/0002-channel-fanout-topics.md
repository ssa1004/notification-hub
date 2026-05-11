# ADR-0002: 다채널 fan-out — Kafka topic 분리 vs 단일 topic + channel 헤더

## 상태
적용 (topic 분리)

## 배경
한 알림이 여러 채널 (PUSH / EMAIL / SMS / KAKAO_ALIMTALK) 로 fan-out 됩니다. 각 채널의
worker 가 Kafka consume 으로 vendor 호출을 진행합니다. 두 가지 설계가 가능합니다.

A) **단일 topic** — `notification.delivery` 하나에 모든 채널 메시지를 싣고, 헤더
   `channel-type` 으로 분기. consumer 가 자기 채널이 아니면 skip.

B) **채널별 topic** — `notification.delivery.push`, `notification.delivery.email`,
   `notification.delivery.sms`, `notification.delivery.kakao_alimtalk` 별도 topic.
   consumer-group 도 채널별 분리.

## 결정
**채널별 topic 분리** (방식 B).

## 결과
- 채널마다 처리량 / 가격 / SLA 가 매우 다름
  - SMS / 알림톡 은 vendor 호출당 비용이 높고 분당 처리량 한도가 낮음 (vendor 측 throttling)
  - PUSH / EMAIL 은 비용 저렴하고 throughput 높음
  - 같은 topic 에서 처리하면 SMS 가 막혀도 PUSH consumer 가 영향 받음 (head-of-line blocking)
- DLQ 정책도 채널별로 다름 — 알림톡은 vendor reject 가 정책상 영구인 경우가 많아 즉시
  EXHAUSTED, 반면 SES는 일시 throttling 이 흔해 retry 가치가 큼
- consumer-group 분리 → 채널별 lag / 처리량 / 에러율 모니터링 / 알람 분리 가능
- partition key 는 모든 topic 공통으로 `notificationId` — 같은 알림은 순서 보존
- (단점) topic 4개 운영 부담 (topic 생성 / config 분리). 채널 추가 시 topic 1개 늘어남.
  자동화 가능한 수준이고 채널 추가가 매우 빈번하지 않으므로 수용
- (단점) 모니터링/알람 대시보드도 4세트. Grafana variable 로 channel 만 templating 하면 1세트로 가능

## 다시 검토할 시점
채널이 10개 이상으로 늘어나거나, 채널당 throughput 차이가 크지 않게 평준화되면 단일 topic +
헤더 라우팅 으로 회귀를 검토.
