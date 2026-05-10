#!/usr/bin/env bash
# Cross-repo 통합 시연 — docker-compose.integration.yml 가 띄운 stub 묶음 위에서
# "도메인 event 도착 → 발송 → vendor mock 호출 → security-sink 도달" 한 사이클 실행.
#
# 전제: `docker compose -f infrastructure/docker-compose.integration.yml up -d` 가 이미 실행됨.
#
# 본 hub 코드는 외부 도메인 event 를 직접 consume 하지 않습니다 (현재 설계는 자기 발송용
# Kafka topic 만 listen). 그래서 이 시연 스크립트는 도메인 service 의 어플리케이션 영역을
# 모사하는 역할입니다 — sample event 를 Kafka 로 발사한 뒤, 같은 event 를 본 hub 의
# REST API 로 호출해 발송을 트리거합니다.
#
# vendor 호출 결과를 security-log-search 로 흘려보내는 것도 본 hub 가 직접 하지 않으므로
# 이 스크립트가 hub 의 응답을 보고 결과 line / alert.fired 를 시연용으로 publish 합니다.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="$ROOT/infrastructure/docker-compose.integration.yml"
DC=(docker compose -f "$COMPOSE_FILE")

HUB_URL="${HUB_URL:-http://localhost:8088}"
RECIPIENT_ID="${RECIPIENT_ID:-demo-user-1}"

log() { printf '\n[demo] %s\n' "$*"; }

# 0. compose 가 띄워져 있는지 확인.
if ! "${DC[@]}" ps --status running --services | grep -q '^notification-hub$'; then
    echo "[demo] notification-hub 가 실행 중이 아닙니다." >&2
    echo "  먼저: docker compose -f infrastructure/docker-compose.integration.yml up -d" >&2
    exit 1
fi

# 1. mock JWT 발급. auth-service 가 발행할 service-account JWT 를 모사 (시연용 평문 — 본 hub
#    가 실제 JWT 검증을 활성화하기 전까지는 헤더 형태만 의미가 있음).
log "1. mock JWT 발급"
HEADER=$(printf '{"alg":"RS256","kid":"auth-stub-2026-05","typ":"JWT"}' \
    | base64 | tr -d '=\n' | tr '/+' '_-')
PAYLOAD=$(printf '{"iss":"https://auth-stub/","sub":"svc-resell-orderbook","aud":"notification-hub","scope":"notify:send"}' \
    | base64 | tr -d '=\n' | tr '/+' '_-')
JWT="$HEADER.$PAYLOAD.stub-signature"
echo "  JWT (앞 60자): ${JWT:0:60}..."

# 2. recipient seed — 실 운영에서는 user/auth service 가 master.
log "2. recipient seed (postgres)"
"${DC[@]}" exec -T postgres psql -U notification -d notification -v ON_ERROR_STOP=1 <<SQL
INSERT INTO recipient (id, channels_json, locale, timezone) VALUES (
    '$RECIPIENT_ID',
    '[{"type":"PUSH","address":"$(printf 'p%.0s' {1..160})"},{"type":"EMAIL","address":"demo@example.com"}]',
    'ko-kr',
    'Asia/Seoul'
) ON CONFLICT (id) DO UPDATE SET channels_json = EXCLUDED.channels_json;

INSERT INTO user_preference (recipient_id, allowed_json, preferred_json, quiet_start, quiet_end, timezone) VALUES (
    '$RECIPIENT_ID',
    '{"SECURITY":true,"TRANSACTIONAL":true,"MARKETING":false,"SERVICE":true}',
    '{"SECURITY":["PUSH","EMAIL"],"TRANSACTIONAL":["PUSH","EMAIL"]}',
    NULL, NULL,
    'Asia/Seoul'
) ON CONFLICT (recipient_id) DO UPDATE SET allowed_json = EXCLUDED.allowed_json, preferred_json = EXCLUDED.preferred_json;

INSERT INTO device_token (id, recipient_id, platform, token, registered_at) VALUES (
    gen_random_uuid(),
    '$RECIPIENT_ID',
    'IOS',
    '$(printf 'p%.0s' {1..160})',
    now()
) ON CONFLICT (token) DO NOTHING;
SQL

# 3. 도메인 service 가 발행할 sample event 를 Kafka 로 발사.
#    실 시스템에서는 resell-orderbook 의 outbox relay 가 이 topic 으로 publish 합니다.
log "3. domain event 발사 (order.created)"
EVENT_JSON=$(cat <<JSON
{"eventType":"order.created","orderId":"ORD-$(date +%s)","buyerId":"$RECIPIENT_ID","amount":129000,"sku":"AJ1-RETRO-HIGH"}
JSON
)
echo "  event: $EVENT_JSON"
"${DC[@]}" exec -T domain-producer bash -c "
    /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 \
        --create --if-not-exists --topic order.created \
        --partitions 1 --replication-factor 1 >/dev/null
    echo '$EVENT_JSON' | /opt/kafka/bin/kafka-console-producer.sh \
        --bootstrap-server kafka:9092 --topic order.created
"

# 4. 도메인 service 의 어플리케이션 영역 모사 — 같은 event 를 받아 알림 hub REST 호출.
#    Idempotency-Key 는 orderId 1:1 로 잡아서 같은 event 재처리도 발송 1회만 보장.
log "4. notification-hub REST 호출 (Bearer JWT, Idempotency-Key)"
ORDER_ID=$(printf '%s' "$EVENT_JSON" | sed -n 's/.*"orderId":"\([^"]*\)".*/\1/p')
RESP=$(curl -s -o /tmp/notify-resp.json -w "%{http_code}" -X POST \
    "$HUB_URL/api/v1/notifications" \
    -H "Authorization: Bearer $JWT" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: order-created-$ORDER_ID" \
    -d "$(cat <<JSON
{
    "recipientId": "$RECIPIENT_ID",
    "kind": "TRANSACTIONAL",
    "title": "주문 접수",
    "body": "$ORDER_ID 주문이 접수되었습니다."
}
JSON
)")
echo "  HTTP $RESP"
cat /tmp/notify-resp.json
echo

if [[ "$RESP" != "202" && "$RESP" != "200" ]]; then
    echo "[demo] 발송 호출 실패. 위 응답 확인." >&2
    exit 1
fi

NOTIFICATION_ID=$(sed -n 's/.*"notificationId":"\([^"]*\)".*/\1/p' /tmp/notify-resp.json)

# 5. vendor mock 호출이 비동기로 끝나기를 잠깐 대기 + hub 로그에서 vendor 호출 라인 추출.
#    실 시스템이라면 본 hub 의 audit logger 가 발행하는 vendor.dispatch.* metric 을 본다.
log "5. vendor mock 비동기 호출 대기 (hub log tail)"
sleep 5
"${DC[@]}" logs --tail 200 notification-hub 2>&1 | grep -E "(audit|dispatch|vendor)" | tail -10 || true

# 6. vendor 호출 결과를 security-log-search 가 받을 형태로 발행.
#    실 시스템에서는 본 hub 의 별도 sink adapter 가 담당. 시연 스크립트에서 그 역할만 모사.
log "6. notify.vendor.result publish (security-sink 가 별도 consumer 로 확인)"
RESULT_LINE=$(cat <<JSON
{"event":"notify.vendor.result","notificationId":"$NOTIFICATION_ID","status":"SUCCEEDED","ts":"$(date -u +%Y-%m-%dT%H:%M:%SZ)"}
JSON
)
echo "  $RESULT_LINE"
"${DC[@]}" exec -T domain-producer bash -c "
    echo '$RESULT_LINE' | /opt/kafka/bin/kafka-console-producer.sh \
        --bootstrap-server kafka:9092 --topic notify.vendor.result
"

# 7. 영구 실패 시나리오 — alert.fired 를 security-sink 로 직접 발행해 cross-repo 도달까지 시연.
#    실 시스템에서는 본 hub 가 EXHAUSTED 가 된 attempt 에 대해 자동으로 publish.
log "7. alert.fired publish — security-sink 가 stdout 으로 echo"
ALERT=$(cat <<JSON
{"event":"alert.fired","rule":"NOTIFY_VENDOR_EXHAUSTED","notificationId":"$NOTIFICATION_ID","severity":"warning","ts":"$(date -u +%Y-%m-%dT%H:%M:%SZ)"}
JSON
)
echo "  $ALERT"
"${DC[@]}" exec -T domain-producer bash -c "
    echo '$ALERT' | /opt/kafka/bin/kafka-console-producer.sh \
        --bootstrap-server kafka:9092 --topic alert.fired
"

log "8. security-sink 마지막 5줄"
"${DC[@]}" logs --tail 5 security-sink || true

log "완료. JWK Set 확인: curl http://localhost:8085/.well-known/jwks.json"
