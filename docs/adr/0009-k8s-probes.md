# ADR-0009: K8s 3종 probe (startup/readiness/liveness)

## 상태
적용

## 배경
운영 중 자주 보는 사고:

- **부팅 직후 트래픽 유입** — Flyway 마이그레이션 / JPA 초기화 / Kafka producer 첫
  연결 전에 Service endpoint 가 pod 를 ready 로 인식 → 첫 N건이 500.
- **Kafka 일시 단절로 전체 pod 재기동** — `KafkaHealthIndicator` 가 liveness 까지 fail
  시키면 K8s 가 pod 죽임 → Kafka 복구되어도 모든 pod 가 동시에 부팅 중 → cascade 장애.
- **Service 에 "ready" 인 pod 가 죽어가는 의존을 가짐** — readiness 가 외부 의존 상태를
  반영 못하면 LB 가 죽은 pod 로 트래픽.

## 결정
3종 probe 를 역할 분리해서 명시.

| Probe | 책임 | 무엇을 본다 |
|---|---|---|
| **startup** | 부팅 완료 신호. readiness/liveness 가 부팅 중인 pod 를 죽이지 못하게 차단 | `/actuator/health/readiness` (failureThreshold 40 × 3s = 약 2분) |
| **readiness** | 트래픽 받을 준비 — 외부 의존 (Kafka/Redis) 도 체크 | `/actuator/health/readiness`. `ApplicationReadinessCoordinator` 가 5s 주기 ping → `REFUSING_TRAFFIC` 토글 |
| **liveness** | process 자체가 살아있는가 — 외부 의존과 무관 | `/actuator/health/liveness`. JVM deadlock / OOM 직전이면 fail |

`management.endpoint.health.group` 으로 그룹별 indicator 분리:

```yaml
group:
  readiness:
    include: readinessState
  liveness:
    include: livenessState
health:
  kafka.enabled: false   # 기본 KafkaHealthIndicator 끄기
  redis.enabled: false
```

기본 `KafkaHealthIndicator` 는 readiness/liveness 양쪽에 들어가 cascade 장애를 만들 수 있어
끄고, 자체 `ApplicationReadinessCoordinator` 가 readiness 만 따로 토글.

## 결과
- **부팅 race 없음** — startup 완료 전엔 readiness/liveness 모두 "PROVISIONAL" — Service
  endpoint 에서 빠진 채 부팅. Flyway 가 30초 걸려도 K8s 가 pod 죽이지 않음.
- **Kafka 일시 단절 ≠ pod 재기동** — readiness 만 REFUSING → Service endpoint 에서 빠짐
  (트래픽 없음). 의존 복구되면 5s 안에 자동 복귀 — pod 자체는 살아있음.
- **liveness 가 "정말 죽었는가" 만 판정** — JVM 자체 hang 같은 진짜 재기동이 필요한 경우만
  K8s 가 죽임.
- (단점) `ApplicationReadinessCoordinator` 가 5s 주기로 Redis ping + Kafka clusterId 호출 —
  network RTT × 2회 / 5s 의 추가 부하. Redis/Kafka 가 살아있을 땐 ms 단위.
- (단점) `health.kafka.enabled=false` 이므로 actuator 의 health 화면에 Kafka 가 안 보임 —
  운영자가 "Kafka 가 살아있나" 확인하려면 readiness 상태 + Kafka 자체 metric 봐야 함.

## 다시 검토할 시점
- 의존이 늘어나면 (외부 ML 모델 서버, 결제 게이트웨이 등) `ApplicationReadinessCoordinator`
  가 그 의존도 체크해야 함. 의존 종류가 5개 넘으면 Strategy pattern 으로 분리.
- multi-cluster (active-active) 로 가면 readiness 가 단순 ping 이 아니라 cross-cluster
  consensus 까지 봐야 함 — Raft/etcd 합의 상태 등.

## 용어 풀이 (쉽게)

- **probe (프로브)** — 쿠버네티스가 주기적으로 던지는 '건강 체크' 질문. 대답을 보고 트래픽을 줄지, 다시 살릴지를 정한다.
- **startup / readiness / liveness probe** — startup은 '부팅 다 됐니?', readiness는 '손님 받을 준비 됐니?', liveness는 '아예 죽은 건 아니지?'를 묻는 세 가지 검사. 역할을 나눠 부팅 중인 pod를 실수로 죽이지 않게 한다.
- **cascade 장애 (연쇄 장애)** — 한 곳의 지연·죽음이 도미노처럼 옆으로 번지는 장애. Kafka가 잠깐 끊겼다고 모든 pod를 동시에 재기동하면 복구가 더 늦어진다.
- **active-active (액티브-액티브)** — 여러 클러스터를 동시에 켜놓고 둘 다 실제 트래픽을 받게 하는 구성. 한쪽이 죽어도 다른 쪽이 계속 일한다.
- **consensus / Raft·etcd (합의)** — 여러 서버가 "지금 상태가 뭐다"에 다수결로 한목소리를 내는 알고리즘. 서로 자기가 맞다고 우기는 혼선을 막는다.
