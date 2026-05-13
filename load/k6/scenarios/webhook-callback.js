// vendor 측 webhook ack 부하.
//
// 시나리오 의도:
//   - vendor (FCM/SES/Twilio/Kakao) 의 콜백을 모사 — `POST /api/v1/deliveries/{id}/ack`
//     (Helm ingress 에서는 `/webhooks/*` prefix 로 노출되는 path). HMAC-SHA256 서명 + 5분
//     replay window 검증을 통과한 콜백만 200, 그 외는 401.
//   - HMAC 계산 + 5분 윈도우 정상 path 의 throughput 을 보고, 의도적으로 잘못된 서명을
//     섞어 (k6 의 매 N 번째 iteration) fail-closed 가 깨지지 않는지도 동시에 확인.
//
//   * 실제 deliveryAttemptId 는 미리 발행된 알림으로부터 알아내야 하지만, 본 시나리오는
//     서명 검증 + replay window + 매핑 lookup 까지의 부하 측정이 목적이라 임의 UUID 를
//     사용한다. 검증을 통과하면 AcknowledgeDeliveryUseCase 가 attempt 못 찾고 404 응답
//     (ATTEMPT_NOT_FOUND) — 본 시나리오는 401 / 200 / 404 모두 "검증 단계가 동작한 신호" 로
//     본다. 실제 200 트래픽이 필요한 경우 사전에 발송 시나리오를 흘리고 그 id 를 풀로 주입.
//
// thresholds:
//   - http_req_duration{name:webhook-valid} p95 < 50ms — HMAC SHA256 + Mac equality 만의
//                                                       빠른 path.
//   - webhook_hmac_fail rate == 0 — 정상 path 의 HMAC 실패는 0 건이어야 한다.
//   - webhook_replay_rejected rate > 0.99 — 의도적으로 흘린 replay (timestamp -10분) 가
//                                          모두 401 로 거절되어야 한다.
//   - webhook_signature_tamper_rejected rate > 0.99 — 의도적으로 흘린 잘못된 서명도 모두
//                                                    401.

import http from 'k6/http';
import crypto from 'k6/crypto';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { BASE_URL, VENDORS } from '../lib/config.js';

const hmacFail = new Rate('webhook_hmac_fail');
const replayRejected = new Rate('webhook_replay_rejected');
const tamperRejected = new Rate('webhook_signature_tamper_rejected');
const validOk = new Counter('webhook_valid_processed');

// vendor 별 secret. k6 cli 의 env 로 주입 — `K6_WEBHOOK_SECRET_FCM=xxx k6 run ...`.
// 운영 helm secret 과 정확히 같은 값을 외부에서 주입해 본 시나리오가 진짜 endpoint 를 통과하게.
function secretFor(vendor) {
  const upper = vendor.toUpperCase();
  return __ENV[`K6_WEBHOOK_SECRET_${upper}`] || 'load-test-secret';
}

export const options = {
  scenarios: {
    webhook: {
      executor: 'constant-arrival-rate',
      rate: 500,                 // 초당 500 req — webhook 은 가장 가벼운 검증 경로라 더 빡세게.
      timeUnit: '1s',
      duration: '45s',
      preAllocatedVUs: 50,
      maxVUs: 250,
    },
  },
  thresholds: {
    'http_req_duration{name:webhook-valid}': ['p(95)<50', 'p(99)<150'],
    webhook_hmac_fail: ['rate==0'],
    webhook_replay_rejected: ['rate>0.99'],
    webhook_signature_tamper_rejected: ['rate>0.99'],
  },
};

/**
 * 한 vendor 로 정상 서명한 ack 요청을 보낸다.
 */
function sendValid(vendor, attemptId) {
  const secret = secretFor(vendor);
  const body = JSON.stringify({ success: true, vendorMessageId: `vm-${attemptId.slice(0, 8)}` });
  const timestamp = Date.now().toString();
  const mac = crypto.hmac('sha256', secret, `${timestamp}.${body}`, 'hex');
  const signature = `v1=${mac}`;

  const res = http.post(`${BASE_URL}/api/v1/deliveries/${attemptId}/ack`, body, {
    headers: {
      'Content-Type': 'application/json',
      'X-Notification-Hub-Vendor': vendor,
      'X-Notification-Hub-Signature': signature,
      'X-Notification-Hub-Timestamp': timestamp,
    },
    tags: { name: 'webhook-valid' },
  });

  // 정상 서명은 401 이 아니어야 한다. ATTEMPT_NOT_FOUND (404) / 202 둘 다 검증 통과한 신호.
  const passedVerification = res.status !== 401;
  hmacFail.add(!passedVerification);
  if (passedVerification) validOk.add(1);
  check(res, {
    'valid: not 401': (r) => r.status !== 401,
    'valid: not 5xx': (r) => r.status < 500,
  });
}

/**
 * replay 시뮬레이션 — timestamp 가 윈도우 (5분) 밖. 401 이 정상.
 */
function sendReplay(vendor, attemptId) {
  const secret = secretFor(vendor);
  const body = JSON.stringify({ success: true, vendorMessageId: `replay-${attemptId.slice(0, 8)}` });
  // 10 분 전 timestamp — REPLAY_WINDOW_MS (5분) 의 두 배.
  const timestamp = (Date.now() - 10 * 60 * 1000).toString();
  const mac = crypto.hmac('sha256', secret, `${timestamp}.${body}`, 'hex');

  const res = http.post(`${BASE_URL}/api/v1/deliveries/${attemptId}/ack`, body, {
    headers: {
      'Content-Type': 'application/json',
      'X-Notification-Hub-Vendor': vendor,
      'X-Notification-Hub-Signature': `v1=${mac}`,
      'X-Notification-Hub-Timestamp': timestamp,
    },
    tags: { name: 'webhook-replay' },
  });

  replayRejected.add(res.status === 401);
  check(res, { 'replay: 401': (r) => r.status === 401 });
}

/**
 * 서명 변조 — body 는 같지만 서명만 다른 secret 으로 계산. 401 이 정상.
 */
function sendTampered(vendor, attemptId) {
  const body = JSON.stringify({ success: true, vendorMessageId: `tampered-${attemptId.slice(0, 8)}` });
  const timestamp = Date.now().toString();
  // 의도적으로 잘못된 secret — fail-closed 동작 확인.
  const mac = crypto.hmac('sha256', 'wrong-secret', `${timestamp}.${body}`, 'hex');

  const res = http.post(`${BASE_URL}/api/v1/deliveries/${attemptId}/ack`, body, {
    headers: {
      'Content-Type': 'application/json',
      'X-Notification-Hub-Vendor': vendor,
      'X-Notification-Hub-Signature': `v1=${mac}`,
      'X-Notification-Hub-Timestamp': timestamp,
    },
    tags: { name: 'webhook-tampered' },
  });

  tamperRejected.add(res.status === 401);
  check(res, { 'tampered: 401': (r) => r.status === 401 });
}

/**
 * 매 iteration 마다 임의 UUID 를 deliveryAttemptId 로 사용. k6 의 stdlib uuid 가 없어
 * 간단한 hex 조합으로 충분히 unique.
 */
function randomUuid() {
  const hex = (n) => Math.floor((1 + Math.random()) * 16 ** n).toString(16).slice(1);
  return `${hex(8)}-${hex(4)}-4${hex(3)}-${(8 + Math.floor(Math.random() * 4)).toString(16)}${hex(3)}-${hex(12)}`;
}

export default function () {
  const vendor = VENDORS[__ITER % VENDORS.length];
  const attemptId = randomUuid();

  // iteration 의 80% 는 정상 서명, 10% replay, 10% tampered — 한 시나리오에서 세 경로 동시 측정.
  const r = __ITER % 10;
  if (r < 8) {
    sendValid(vendor, attemptId);
  } else if (r === 8) {
    sendReplay(vendor, attemptId);
  } else {
    sendTampered(vendor, attemptId);
  }

  sleep(0.01);
}
