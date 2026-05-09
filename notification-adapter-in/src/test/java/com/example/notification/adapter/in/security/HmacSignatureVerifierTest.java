package com.example.notification.adapter.in.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignatureVerifierTest {

    private static final String SECRET = "shared-secret-for-vendor-x";
    private static final byte[] BODY = "{\"status\":\"DELIVERED\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void verify_validSignatureAndTimestamp_returnsTrue() {
        long now = 1_715_000_000_000L;
        String sig = HmacSignatureVerifier.SIG_PREFIX
                + HmacSignatureVerifier.computeHexHmac(SECRET, now, BODY);

        boolean ok = HmacSignatureVerifier.verify(SECRET, sig, Long.toString(now), BODY, now);

        assertThat(ok).isTrue();
    }

    /** body 한 byte 만 바뀌어도 거절 (가짜 콜백 시도). */
    @Test
    void verify_tamperedBody_returnsFalse() {
        long now = 1_715_000_000_000L;
        String sig = HmacSignatureVerifier.SIG_PREFIX
                + HmacSignatureVerifier.computeHexHmac(SECRET, now, BODY);
        byte[] tampered = "{\"status\":\"FAILED\"}".getBytes(StandardCharsets.UTF_8);

        boolean ok = HmacSignatureVerifier.verify(SECRET, sig, Long.toString(now), tampered, now);

        assertThat(ok).isFalse();
    }

    /** 다른 secret 으로 만든 서명은 거절. */
    @Test
    void verify_wrongSecret_returnsFalse() {
        long now = 1_715_000_000_000L;
        String sig = HmacSignatureVerifier.SIG_PREFIX
                + HmacSignatureVerifier.computeHexHmac("OTHER-SECRET", now, BODY);

        boolean ok = HmacSignatureVerifier.verify(SECRET, sig, Long.toString(now), BODY, now);

        assertThat(ok).isFalse();
    }

    /** 5분+1초 지난 timestamp 는 replay 로 간주, 거절. */
    @Test
    void verify_oldTimestamp_returnsFalse() {
        long signedAt = 1_715_000_000_000L;
        long now = signedAt + HmacSignatureVerifier.REPLAY_WINDOW_MS + 1_000L;
        String sig = HmacSignatureVerifier.SIG_PREFIX
                + HmacSignatureVerifier.computeHexHmac(SECRET, signedAt, BODY);

        boolean ok = HmacSignatureVerifier.verify(SECRET, sig, Long.toString(signedAt), BODY, now);

        assertThat(ok).isFalse();
    }

    /** 미래로 5분+1초 가도 똑같이 거절 — 서버 시계 오차 / 의도된 future-dating 모두 차단. */
    @Test
    void verify_futureTimestamp_returnsFalse() {
        long now = 1_715_000_000_000L;
        long signedAt = now + HmacSignatureVerifier.REPLAY_WINDOW_MS + 1_000L;
        String sig = HmacSignatureVerifier.SIG_PREFIX
                + HmacSignatureVerifier.computeHexHmac(SECRET, signedAt, BODY);

        boolean ok = HmacSignatureVerifier.verify(SECRET, sig, Long.toString(signedAt), BODY, now);

        assertThat(ok).isFalse();
    }

    /** prefix 없거나 잘못된 형식이면 거절. */
    @Test
    void verify_missingOrInvalidPrefix_returnsFalse() {
        long now = 1_715_000_000_000L;
        String hex = HmacSignatureVerifier.computeHexHmac(SECRET, now, BODY);

        assertThat(HmacSignatureVerifier.verify(SECRET, hex, Long.toString(now), BODY, now))
                .isFalse();
        assertThat(HmacSignatureVerifier.verify(SECRET, "v2=" + hex, Long.toString(now), BODY, now))
                .isFalse();
        assertThat(HmacSignatureVerifier.verify(SECRET, null, Long.toString(now), BODY, now))
                .isFalse();
    }

    @Test
    void verify_missingTimestamp_returnsFalse() {
        long now = 1_715_000_000_000L;
        String sig = HmacSignatureVerifier.SIG_PREFIX
                + HmacSignatureVerifier.computeHexHmac(SECRET, now, BODY);

        assertThat(HmacSignatureVerifier.verify(SECRET, sig, null, BODY, now)).isFalse();
        assertThat(HmacSignatureVerifier.verify(SECRET, sig, "not-a-number", BODY, now)).isFalse();
    }

    @Test
    void verify_emptySecret_returnsFalse() {
        long now = 1_715_000_000_000L;
        String sig = HmacSignatureVerifier.SIG_PREFIX + HmacSignatureVerifier.computeHexHmac(SECRET, now, BODY);

        assertThat(HmacSignatureVerifier.verify(null, sig, Long.toString(now), BODY, now)).isFalse();
        assertThat(HmacSignatureVerifier.verify("", sig, Long.toString(now), BODY, now)).isFalse();
    }
}
