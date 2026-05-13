// GET /api/v1/notifications/me?cursor=... cursor pagination 부하.
//
// 시나리오 의도:
//   - 알림 누적이 많아진 사용자의 이력 페이지네이션 — offset 방식의 deep-page 비용을 피하기
//     위해 직전 페이지 마지막 row 의 id 를 cursor 로 받는 구현. 본 시나리오는 read-heavy
//     트래픽에서 cursor 분기의 두 경로가 모두 200 인지를 회귀 가드한다.
//
//   - **회귀 가드 — cursor 없는 호출의 Postgres NULL 추론 500**:
//     commit `ee7d3a8 fix(adapter-out): cursor 없는 이력 조회를 두 쿼리로 분리`. JdbcTemplate
//     의 NULL 파라미터가 PreparedStatement 단계에서 타입 추론 실패 → 500 INTERNAL_ERROR.
//     fix 가 두 쿼리 (cursor=NULL 분기와 그 외) 로 분리. 본 시나리오는 cursor 없는 GET 을
//     매 iteration 50% 비율로 호출해 그 분기가 다시 깨졌을 때 즉시 빨갛게 만든다.
//
// thresholds:
//   - http_req_failed rate < 1% — 500 이 1% 이상 나오면 cursor=null 회귀를 즉시 감지.
//   - http_req_duration p95 < 150ms — index 가 (recipient_id, created_at DESC) 라 가벼움.
//   - history_no_cursor_ok rate > 0.99 — cursor 없는 호출의 200 비율 (위 회귀 가드의 핵심).
//   - history_with_cursor_ok rate > 0.99 — cursor 있는 호출의 200 비율.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter } from 'k6/metrics';
import { BASE_URL, pickRecipient } from '../lib/config.js';
import { authHeader } from '../lib/auth.js';

const noCursorOk = new Rate('history_no_cursor_ok');
const withCursorOk = new Rate('history_with_cursor_ok');
const cursorFollowed = new Counter('history_cursor_followed');

export const options = {
  scenarios: {
    history: {
      executor: 'constant-arrival-rate',
      rate: 150,                 // 초당 150 req — read endpoint 이므로 단일 채널 발송보다 더 강하게.
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 30,
      maxVUs: 150,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<150', 'p(99)<400'],
    history_no_cursor_ok: ['rate>0.99'],
    history_with_cursor_ok: ['rate>0.99'],
  },
};

export default function () {
  const recipient = pickRecipient(__VU, __ITER);

  // 첫 페이지 — cursor 없는 호출. 회귀 가드의 핵심 분기.
  const firstUrl = `${BASE_URL}/api/v1/notifications/me?recipientId=${encodeURIComponent(recipient)}&limit=20`;
  const first = http.get(firstUrl, {
    headers: authHeader(),
    tags: { name: 'history-cursor-first' },
  });

  const firstOk = first.status === 200;
  noCursorOk.add(firstOk);
  check(first, {
    'first page 200': (r) => r.status === 200,
    'first page body looks like page': (r) => {
      const body = r.body || '';
      // DeliveryHistoryPage 는 items + nextCursor (또는 cursor) 의 JSON 객체.
      return body.startsWith('{') && (body.includes('items') || body.includes('cursor') || body.includes('nextCursor'));
    },
  });

  // 두 번째 페이지 — body 에서 cursor 를 뽑아 사용. 정규식 추출 (k6 의 JSON 파싱 부담 회피).
  // 사용자 이력이 적어 nextCursor 가 비어있을 수도 있다 — 그 때는 가공된 UUID 로 호출해
  // cursor 분기 자체의 200 동작을 확인.
  let cursor = null;
  if (firstOk) {
    const match = (first.body || '').match(/"nextCursor"\s*:\s*"([0-9a-fA-F-]{36})"/);
    if (match) cursor = match[1];
  }
  if (!cursor) {
    // 임의 UUID — 매칭되는 row 가 없으면 빈 페이지 (200 + items=[]) 가 정상.
    cursor = '00000000-0000-0000-0000-000000000000';
  }

  const nextUrl = `${BASE_URL}/api/v1/notifications/me?recipientId=${encodeURIComponent(recipient)}&cursor=${cursor}&limit=20`;
  const next = http.get(nextUrl, {
    headers: authHeader(),
    tags: { name: 'history-cursor-next' },
  });

  withCursorOk.add(next.status === 200);
  cursorFollowed.add(1);
  check(next, {
    'next page 200': (r) => r.status === 200,
  });

  sleep(0.1);
}
