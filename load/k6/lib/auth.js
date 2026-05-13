// mock JWT helper — k6 시나리오에서 Authorization 헤더에 붙일 토큰을 만든다.
//
// notification-hub 의 본 시점 SecurityConfig 는 운영자 admin endpoint (X-Admin-Token)
// 와 webhook 의 HMAC 서명만 강제하고, /api/v1/notifications 계열은 별도 JWT 검증을
// 활성화하지 않은 상태 (의도된 후속 작업 — README 의 Portfolio Set 통합 절 참고).
// 호출 서비스가 service-account JWT 를 붙여 보내는 형태만 유지해 두면 추후 JWT
// resource-server 의존을 활성화해도 부하 시나리오는 그대로 재사용 가능하다.
//
// 따라서 두 가지 경로를 지원한다:
//   1) K6_TOKEN env 가 비어 있으면 빈 헤더 — dev / 통합 환경에서 그대로 통과.
//   2) K6_TOKEN 이 있으면 Bearer 로 부착 — auth-service / auth-stub 발급 토큰을 외부에서
//      주입하는 케이스.

import encoding from 'k6/encoding';

const ENV_TOKEN = __ENV.K6_TOKEN || '';

/**
 * Authorization 헤더 객체를 돌려준다. 토큰이 비어 있으면 빈 객체.
 */
export function authHeader() {
  if (!ENV_TOKEN) return {};
  return { Authorization: `Bearer ${ENV_TOKEN}` };
}

/**
 * 토큰 raw 값을 돌려준다 — 진단용.
 */
export function rawToken() {
  return ENV_TOKEN;
}

/**
 * 테스트용 unsigned JWT — kid 가 맞지 않으면 운영에서는 reject. dev 에서만 의미 있음.
 * jwt.io 호환 base64url 인코딩. 서명은 sha256 가 k6 stdlib 에 없어 빈 값으로 둔다.
 *
 * @param subject {string} — sub claim
 * @param ttlSeconds {number} — exp 까지의 초
 */
export function unsignedJwt(subject = 'k6-load', ttlSeconds = 3600) {
  const header = { alg: 'none', typ: 'JWT' };
  const now = Math.floor(Date.now() / 1000);
  const payload = {
    sub: subject,
    iat: now,
    exp: now + ttlSeconds,
    scope: 'notify:send',
  };
  const part = (o) => base64url(JSON.stringify(o));
  return `${part(header)}.${part(payload)}.`;
}

function base64url(s) {
  return encoding.b64encode(s, 'rawurl');
}
