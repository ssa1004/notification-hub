// POST /api/v1/notifications 단일 채널 (PUSH) 발송 부하.
//
// 시나리오 의도:
//   - SECURITY kind 로 보내면 ChannelResolver 가 사용자 preference / DND 와 무관하게
//     mandatory + bypass-quiet-hours 경로를 탄다 — 발송 결정 자체는 분기 없이 빠른 단일
//     경로.
//   - sample seed 사용자가 PUSH 1 채널만 preference 에 들어있게 미리 구성하면 한 발송이
//     PUSH attempt 1건만 생성. 본 시나리오는 그 상태에서 throughput / latency 를 본다.
//
// thresholds:
//   - http_req_duration p95 < 100ms — Redis idempotency + DB write + Outbox INSERT 의 합.
//                                     Spring Boot virtual thread + HikariCP 20 풀로 충분.
//   - http_req_failed rate < 1% — 일부 409 (idempotency 중복) 가 끼면 5% 까지 허용해도 OK.
//                                  본 시나리오는 매번 새 idempotency key 라 0 에 가까워야 함.
//   - notif_send_accepted rate > 95% — 202 + 200(SUPPRESSED) 둘 다 정상 응답.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { BASE_URL, pickRecipient, newIdempotencyKey } from '../lib/config.js';
import { authHeader } from '../lib/auth.js';

const acceptedRate = new Rate('notif_send_accepted');

export const options = {
  scenarios: {
    single_channel: {
      executor: 'constant-arrival-rate',
      rate: 200,                 // 초당 200 req
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<100', 'p(99)<250'],
    notif_send_accepted: ['rate>0.95'],
  },
};

export default function () {
  const recipient = pickRecipient(__VU, __ITER);
  const idem = newIdempotencyKey();

  const payload = JSON.stringify({
    recipientId: recipient,
    kind: 'SECURITY',
    title: 'OTP',
    body: `code-${idem.slice(0, 6)}`,
  });

  const res = http.post(`${BASE_URL}/api/v1/notifications`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idem,
      ...authHeader(),
    },
    tags: { name: 'notify-single-channel' },
  });

  const ok = check(res, {
    'status 2xx': (r) => r.status >= 200 && r.status < 300,
    'has notification id (when accepted)': (r) => {
      if (r.status !== 202) return true;
      const body = r.body || '';
      return body.includes('notificationId') || body.includes('id');
    },
  });
  acceptedRate.add(ok);

  sleep(0.1);
}
