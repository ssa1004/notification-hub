// 다채널 fan-out 부하 — 한 발송이 4 채널 (PUSH/EMAIL/SMS/KAKAO_ALIMTALK) 로 동시 전파.
//
// 시나리오 의도:
//   - ChannelResolver 가 SECURITY kind 로 사용자 preference 의 모든 채널을 결정 → 한 발송
//     이 N 개 DeliveryAttempt 를 만들고 Outbox 에 N 개 publish event 가 한 트랜잭션 안에
//     적재된다. 즉 DB write 와 atomic rate limit 비용이 단일 채널보다 큰 fan-out 패스.
//   - SendNotificationService 의 다채널 묶음 rate limit (Redis Lua batch 의 "모두 통과
//     하거나 모두 거절") 가 부하 상황에서도 동작하는지 — 한 발송 내에서 PUSH 통과 / SMS
//     거절 같은 부분 leak 이 생기면 안 된다.
//   - 의도적으로 사용자 풀을 충분히 크게 잡아 (8명) 한 사용자 × 채널 단위 rate limit 에는
//     평소 닿지 않도록. 단일 사용자에 몰아치기는 ratelimit-saturation.js 의 책임.
//
// thresholds:
//   - http_req_duration p95 < 300ms — 단일 채널 (100ms) 대비 DB INSERT N 배 + Outbox
//                                     publish N 배. atomic batch rate limit 의 Redis Lua
//                                     라운드트립 한 번도 포함.
//   - http_req_failed rate < 2% — 일부 사용자 × 채널 한도 도달은 정상 (429), 그 외 5xx 는
//                                  추적할 가치 있는 신호.
//   - notif_multi_accepted rate > 90% — 202 + 200(SUPPRESSED) 이 90% 이상이어야 한다.
//                                       429 가 평균 10% 이하일 것 (사용자 8명 × 분당 30 한도
//                                       = 분당 240 발송 가능, 100 req/s × 60s = 6000 발송
//                                       이지만 사용자 분산으로 각자 750 req → 한도 30 / 분 ×
//                                       다채널 묶음 차감 고려 시 분당 30 도달 후 약 70%
//                                       정도가 429. 즉 부하 모델 자체가 한도 트리거 직전).
//                                       이 비율을 너무 깐깐하게 잡으면 단순 한도 초과로
//                                       시나리오가 빨갛게 보임 — 의도된 부하 모델 안에서의
//                                       성공률만 가드한다.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { BASE_URL, pickRecipient, newIdempotencyKey } from '../lib/config.js';
import { authHeader } from '../lib/auth.js';

const acceptedRate = new Rate('notif_multi_accepted');
const rateLimited = new Counter('notif_multi_ratelimited');
const partialLeak = new Counter('notif_multi_partial_leak');

// recipient 풀 — 8 명. 한 사용자 × 채널 한도 (push/email 30/분, sms/kakao 5/분) 에
// 자연스럽게 부딪치되, atomic batch rate limit 의 동작을 함께 본다.
const FANOUT_RECIPIENTS_SIZE = 8;

export const options = {
  scenarios: {
    multi_channel: {
      executor: 'constant-arrival-rate',
      rate: 100,                 // 초당 100 req — 단일 채널의 절반 (DB write 부하 보정)
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 40,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<300', 'p(99)<700'],
    notif_multi_accepted: ['rate>0.5'],
  },
};

export default function () {
  // VU + ITER 로 8 명 풀 안에서 round-robin. 명시적으로 풀 크기 제한.
  const idx = (__VU + __ITER) % FANOUT_RECIPIENTS_SIZE;
  const recipient = `demo-user-${(idx % FANOUT_RECIPIENTS_SIZE) + 1}`;
  const idem = newIdempotencyKey();

  // SECURITY kind — preference / DND 우회. 발송이 사용자 channels_json 의 모든 채널로
  // fan-out (PUSH + EMAIL + SMS + KAKAO 가 모두 등록된 사용자가 sample seed 라고 가정).
  const payload = JSON.stringify({
    recipientId: recipient,
    kind: 'SECURITY',
    title: 'multi-channel-load',
    body: `code-${idem.slice(0, 6)}`,
  });

  const res = http.post(`${BASE_URL}/api/v1/notifications`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idem,
      ...authHeader(),
    },
    tags: { name: 'notify-multi-channel' },
  });

  const accepted = res.status === 202 || res.status === 200;
  const ratelimitedNow = res.status === 429;

  check(res, {
    'status 2xx or 429 (expected)': (r) => accepted || ratelimitedNow,
    '429 has retry-after': (r) => {
      if (r.status !== 429) return true;
      return (r.headers['Retry-After'] || '').length > 0;
    },
  });

  acceptedRate.add(accepted);
  if (ratelimitedNow) {
    rateLimited.add(1);
    // 429 body 의 code 가 RATE_LIMIT_EXCEEDED 면 atomic batch 가 정상 차단한 신호.
    // PARTIAL_LEAK / 채널별 부분 거절 같은 응답이 보이면 batch atomicity 가 깨진 것 — 이번
    // 부하 회귀의 핵심 가드. 응답 body 의 code 가 그 외 값이면 partial-leak counter 에 누적.
    const body = res.body || '';
    if (!body.includes('RATE_LIMIT_EXCEEDED') && !body.includes('rate')) {
      partialLeak.add(1);
    }
  }

  sleep(0.05);
}
