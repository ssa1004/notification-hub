// 시나리오 공통 설정.
//
// BASE_URL 은 환경변수로 덮어쓸 수 있도록. 기본은 docker-compose 의 노출 포트 8080.
// (cross-repo 통합 compose 는 8088 — 그 때는 `BASE_URL=http://localhost:8088` 주입.)

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

/**
 * 4 채널 모두 enum — POST /api/v1/notifications 자체는 채널 종류를 직접 받지 않고,
 * 사용자 preference + ChannelResolver 가 결정하지만, 시나리오 분기 / metric tag /
 * webhook vendor 매핑에 그대로 사용한다.
 */
export const CHANNELS = ['PUSH', 'EMAIL', 'SMS', 'KAKAO_ALIMTALK'];

/**
 * NotificationKind enum — kind 별로 opt-out / DND / rate limit 동작이 다르다.
 *   - SECURITY: mandatory + DND 우회 (어떤 사용자 preference 라도 무조건 발송)
 *   - TRANSACTIONAL: mandatory + DND 준수
 *   - SERVICE / MARKETING: opt-out 가능
 *
 * 부하 시나리오는 SECURITY 를 기본으로 사용해 사용자 preference / DND 분기를 줄이고
 * 단일 경로 (rate limit + fan-out + persist) 의 throughput 만 측정한다.
 */
export const NOTIFICATION_KINDS = ['SECURITY', 'TRANSACTIONAL', 'SERVICE', 'MARKETING'];

/**
 * recipient pool — 시나리오마다 round-robin. 시연 환경에는 `demo-user-1..N` 형태의 seed
 * recipient 가 있다고 가정한다 (scripts/integration-demo.sh 가 1명을 seed; 부하 환경은
 * `scripts/seed-load-recipients.sh` 등으로 N 명을 미리 만들어 둔다 — 본 시나리오의 책임
 * 영역 바깥).
 *
 * 한 VU 가 항상 같은 recipient 를 쓰지 않도록 매 iteration 마다 새 index — rate limit
 * 시나리오는 의도적으로 단일 recipient 를 고정해 한도 트리거를 보기 때문에 따로 처리.
 */
export const RECIPIENTS = (__ENV.K6_RECIPIENTS
  || 'demo-user-1,demo-user-2,demo-user-3,demo-user-4,demo-user-5,demo-user-6,demo-user-7,demo-user-8')
  .split(',')
  .map((s) => s.trim())
  .filter((s) => s.length > 0);

/**
 * VU 인덱스 + iteration 기반 recipient 선택 — 풀을 고르게 분산.
 */
export function pickRecipient(vuId, iter) {
  if (RECIPIENTS.length === 0) return 'demo-user-1';
  return RECIPIENTS[(vuId + iter) % RECIPIENTS.length];
}

/**
 * single-recipient 시나리오 — rate limit saturation 에서 한 명에게 몰아치기 위해.
 */
export const SATURATION_RECIPIENT = __ENV.K6_SATURATION_RECIPIENT || 'demo-user-1';

/**
 * webhook 시나리오의 vendor 풀. WebhookSecrets 가 lower-case key 로 매핑되므로 동일 케이스.
 */
export const VENDORS = ['fcm', 'ses', 'twilio', 'kakao'];

/**
 * Idempotency-Key 생성 — RFC4122 v4 random uuid 형태. k6 의 stdlib 에 uuid 가 없어
 * crypto.randomUUID 를 흉내내는 hex 조합으로 충분히 unique. 같은 키 재요청 시
 * 409 가 떨어지는지 별도 확인하려면 fixed key 를 직접 주입.
 */
export function newIdempotencyKey() {
  // 32-hex char + 4 dash 위치만 보정한 v4 모양. k6 의 Math.random() 은 64bit float 기반.
  const hex = (n) => Math.floor((1 + Math.random()) * 16 ** n).toString(16).slice(1);
  return `${hex(8)}-${hex(4)}-4${hex(3)}-${(8 + Math.floor(Math.random() * 4)).toString(16)}${hex(3)}-${hex(12)}`;
}
