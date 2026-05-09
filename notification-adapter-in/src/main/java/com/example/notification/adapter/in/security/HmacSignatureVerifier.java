package com.example.notification.adapter.in.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;

/**
 * HMAC-SHA256 기반 webhook 서명 검증. 서명 형식:
 *
 * <pre>
 *   X-Notification-Hub-Signature: v1=&lt;hex(HMAC-SHA256(secret, "{timestamp}.{body}"))&gt;
 *   X-Notification-Hub-Timestamp: &lt;epoch-millis&gt;
 * </pre>
 *
 * <p>검증 단계:
 * <ol>
 *   <li>timestamp 가 미래 / 과거 5분 윈도우 밖이면 거절 (replay 차단)
 *   <li>secret 으로 동일한 HMAC 계산 후 {@link MessageDigest#isEqual} 로 timing-safe 비교
 * </ol>
 */
@Slf4j
public final class HmacSignatureVerifier {

    private static final String ALGO = "HmacSHA256";
    /** v1 = "{timestamp}.{body}" 방식. 향후 v2 알고리즘 변경 대비 prefix. */
    public static final String SIG_PREFIX = "v1=";
    /** 5분 = 300_000ms. vendor 가 지연 retry 해도 5분 안엔 도착 가정. */
    public static final long REPLAY_WINDOW_MS = 5 * 60 * 1000L;

    private HmacSignatureVerifier() {}

    /**
     * @return true 이면 검증 통과.
     */
    public static boolean verify(
            String secret, String signatureHeader, String timestampHeader, byte[] body, long now) {
        if (secret == null || secret.isBlank()) {
            log.debug("HMAC 검증 실패: secret 미설정");
            return false;
        }
        if (signatureHeader == null || !signatureHeader.startsWith(SIG_PREFIX)) {
            log.debug("HMAC 검증 실패: signature header 누락 or 잘못된 prefix");
            return false;
        }
        if (timestampHeader == null) {
            log.debug("HMAC 검증 실패: timestamp 헤더 누락");
            return false;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            log.debug("HMAC 검증 실패: timestamp 정수 파싱 실패");
            return false;
        }
        if (Math.abs(now - timestamp) > REPLAY_WINDOW_MS) {
            log.debug(
                    "HMAC 검증 실패: timestamp 윈도우 밖 (now={} ts={} diff={}ms)",
                    now,
                    timestamp,
                    now - timestamp);
            return false;
        }

        String given = signatureHeader.substring(SIG_PREFIX.length());
        String expected = computeHexHmac(secret, timestamp, body);

        // hex 비교 — 길이 다르면 isEqual 가 false. timing-safe.
        return MessageDigest.isEqual(
                given.getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII));
    }

    /** 외부 도구 (테스트 / 운영자 디버깅) 가 같은 알고리즘으로 서명 만들 때 사용. */
    public static String computeHexHmac(String secret, long timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGO));
            mac.update(Long.toString(timestamp).getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(body);
            return toHex(mac.doFinal());
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 계산 실패", e);
        }
    }

    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
