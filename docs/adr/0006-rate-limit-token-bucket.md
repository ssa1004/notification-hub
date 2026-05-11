# ADR-0006: Rate limit — 사용자당 채널별 token bucket

## 상태
적용

## 배경
알림 hub 는 한 사용자에게 짧은 시간 안에 N개 메시지가 폭주하는 것을 막아야 합니다.
이유는 셋:

1. **vendor 비용** — SMS / 알림톡 은 발송당 단가가 큼. 잘못된 cron 이 만 건 보내면 즉시 손실
2. **스팸 신고** — 사용자가 분당 5번 알림을 받으면 채널 전체를 스팸 신고. vendor 가 우리
   reputation 을 떨어뜨림 → 정상 사용자에게도 배달 실패율 증가
3. **사용자 경험** — 실수로 같은 알림이 반복돼도 사용자 입장에선 즉시 짜증

## 결정
**recipient × channel** 별 token bucket. 채널별 차등 한도 — vendor 비용 / 사용자 민감도 반영.

기본값:
- PUSH: 분당 30
- EMAIL: 분당 30
- SMS: 분당 5
- KAKAO_ALIMTALK: 분당 5

구현은 Redis 의 INCR + PEXPIRE 를 Lua 스크립트로 묶어 원자 처리:

```lua
local current = redis.call('INCR', KEYS[1])
if current == 1 then
  redis.call('PEXPIRE', KEYS[1], ARGV[1])
end
local ttl = redis.call('PTTL', KEYS[1])
return {current, ttl}
```

키 형식: `notif:rl:{ChannelType}:{recipientId}`

`SendNotificationService` 는 fan-out 결정 후 각 채널마다 tryConsume 호출. 하나라도 deny 면
`RateLimitExceededException` 으로 발송 자체 거절 (HTTP 429 + Retry-After 헤더). 부분 발송
(3채널 중 2개만 보내고 1개 차단) 은 사용자에게 어떤 채널만 안 왔는지가 더 큰 혼란이라
all-or-nothing 으로 둠.

## 결과
- 같은 사용자 같은 채널 분당 30개 초과는 자동 차단 (push/email)
- vendor 비용 폭주 방지
- HTTP 429 + Retry-After 로 호출자가 backoff 가능
- (단점) Fixed window — 분 경계 직후 burst (1초에 30 + 다음 1초에 30 = 2초 동안 60 발송) 가능.
  운영상 무시 가능 수준이고, sliding window 는 Redis 부하가 더 큼
- (단점) 한도가 정적 — VIP 사용자가 갑자기 폭주 알림 받아야 하는 경우 (예: 회의 알림 연속) 는
  애플리케이션 레벨에서 별도 throttling 우회 keyword 가 필요

## 다시 검토할 시점
- vendor 측 throttling 보다 우리 한도가 항상 먼저 걸리면 한도 상향
- 전체 사용자 중 1% 미만이 한도에 자주 걸리면 한도 적정 — 그 이상이면 한도 재산정
- VIP / 긴급 알림 우회 정책 요구 들어오면 `RateLimitDecision` 에 bypass 플래그 추가
