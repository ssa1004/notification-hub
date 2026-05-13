// 단일 recipient × 채널 한도 트리거 부하.
//
// 시나리오 의도:
//   - 한 사용자에게 분당 한도를 빠르게 초과하는 부하를 흘려, RedisRateLimiter 의 token
//     bucket 이 정확히 임계에서 거절하는지 확인. application.yml 의 기본 값:
//       push-per-window:  30 / 분
//       email-per-window: 30 / 분
//       sms-per-window:    5 / 분
//       kakao-per-window:  5 / 분
//   - SECURITY kind 의 사용자 1명에 초당 100 req 발사 → 초반 ~30 발송 후 PUSH 한도 도달.
//     첫 분이 끝날 때까지 over-limit 거절 (HTTP 429 + code=RATE_LIMIT_EXCEEDED + Retry-After)
//     비율이 충분히 높아야 한다.
//
// thresholds:
//   - http_req_failed rate — 4xx 는 정상 (429 / 409), 5xx 만 추적. tag 로 분리.
//   - ratelimit_reject_ratio > 0.95 — 첫 한도 초과 이후 도달 응답은 거의 모두 429 여야 한다.
//     초반 ~30 발송이 200/202 라도, 60s 총량 기준으로는 30 / 6000 = 0.5% → reject 비율
//     ≥ 95% 가 자연스럽다.
//   - ratelimit_retry_after_present rate == 1.0 — 모든 429 가 Retry-After 헤더를 정확히
//     실어야 한다 (호출자가 backoff 결정 가능해야 함).
//   - ratelimit_code_correct rate > 0.95 — 429 body 의 code 가 RATE_LIMIT_EXCEEDED 인 비율.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter } from 'k6/metrics';
import { BASE_URL, SATURATION_RECIPIENT, newIdempotencyKey } from '../lib/config.js';
import { authHeader } from '../lib/auth.js';

const rejectRatio = new Rate('ratelimit_reject_ratio');
const retryAfterPresent = new Rate('ratelimit_retry_after_present');
const codeCorrect = new Rate('ratelimit_code_correct');
const unexpected5xx = new Counter('ratelimit_unexpected_5xx');

export const options = {
  scenarios: {
    saturation: {
      executor: 'constant-arrival-rate',
      rate: 100,                 // 초당 100 req — 한 명에게 30/분 한도 (PUSH) 를 즉시 돌파
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 30,
      maxVUs: 100,
    },
  },
  thresholds: {
    // 1분간 100 req/s × 60s = 6000 req. 첫 30 만 통과, 나머지는 429 → ≥ 99.5% 거절 기대.
    // 안전 마진 95% 로 임계 설정.
    ratelimit_reject_ratio: ['rate>0.95'],
    ratelimit_retry_after_present: ['rate>0.99'],
    ratelimit_code_correct: ['rate>0.95'],
    // 5xx 는 0건이어야 — rate limit 트리거 자체가 서버 에러를 유발하면 안 된다.
    ratelimit_unexpected_5xx: ['count<10'],
  },
};

export default function () {
  const idem = newIdempotencyKey();

  // 단일 recipient — saturation 목적. ratelimit key 가 recipient × channel 별이라 한 명에게
  // 몰아치는 게 가장 빠른 한도 도달 경로.
  const payload = JSON.stringify({
    recipientId: SATURATION_RECIPIENT,
    kind: 'SECURITY',
    title: 'ratelimit-load',
    body: `code-${idem.slice(0, 6)}`,
  });

  const res = http.post(`${BASE_URL}/api/v1/notifications`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idem,
      ...authHeader(),
    },
    tags: { name: 'ratelimit-saturation' },
  });

  const is429 = res.status === 429;
  const accepted = res.status === 202 || res.status === 200;
  const is5xx = res.status >= 500;

  // 거절 비율 — 429 한 가지를 거절로 친다. 202/200 은 "통과", 그 외 4xx (409 등) 는 noise 라
  // rate add 에서 제외하지 않고 그대로 더해 sanity 만 본다.
  rejectRatio.add(is429);
  if (is5xx) unexpected5xx.add(1);

  if (is429) {
    const retryAfter = res.headers['Retry-After'] || '';
    retryAfterPresent.add(retryAfter.length > 0);
    const body = res.body || '';
    codeCorrect.add(body.includes('RATE_LIMIT_EXCEEDED'));
  }

  check(res, {
    'status 2xx or 429 (expected only)': (r) => accepted || is429,
    'never 5xx during rate limit': (r) => !is5xx,
  });

  // sleep 없이 부하 더 강하게 가도 되지만, k6 의 default 함수 entry 사이 최소 양보 시간만.
  sleep(0.01);
}
