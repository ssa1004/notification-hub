package com.example.notification.adapter.`in`.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.slf4j.LoggerFactory

/**
 * HMAC-SHA256 기반 webhook 서명 검증. 서명 형식:
 *
 * ```
 *   X-Notification-Hub-Signature: v1=<hex(HMAC-SHA256(secret, "{timestamp}.{body}"))>
 *   X-Notification-Hub-Timestamp: <epoch-millis>
 * ```
 *
 * 검증 단계:
 * 1. timestamp 가 미래 / 과거 5분 윈도우 밖이면 거절 (replay 차단)
 * 2. secret 으로 동일한 HMAC 계산 후 [MessageDigest.isEqual] 로 timing-safe 비교
 */
object HmacSignatureVerifier {

    private val log = LoggerFactory.getLogger(HmacSignatureVerifier::class.java)

    private const val ALGO = "HmacSHA256"

    /** v1 = "{timestamp}.{body}" 방식. 향후 v2 알고리즘 변경 대비 prefix. */
    @JvmField
    val SIG_PREFIX = "v1="

    /** 5분 = 300_000ms. vendor 가 지연 retry 해도 5분 안엔 도착 가정. */
    @JvmField
    val REPLAY_WINDOW_MS = 5 * 60 * 1000L

    /**
     * @return true 이면 검증 통과.
     */
    @JvmStatic
    fun verify(
        secret: String?,
        signatureHeader: String?,
        timestampHeader: String?,
        body: ByteArray,
        now: Long,
    ): Boolean {
        if (secret.isNullOrBlank()) {
            log.debug("HMAC 검증 실패: secret 미설정")
            return false
        }
        if (signatureHeader == null || !signatureHeader.startsWith(SIG_PREFIX)) {
            log.debug("HMAC 검증 실패: signature header 누락 or 잘못된 prefix")
            return false
        }
        if (timestampHeader == null) {
            log.debug("HMAC 검증 실패: timestamp 헤더 누락")
            return false
        }
        val timestamp = try {
            timestampHeader.toLong()
        } catch (e: NumberFormatException) {
            log.debug("HMAC 검증 실패: timestamp 정수 파싱 실패")
            return false
        }
        if (Math.abs(now - timestamp) > REPLAY_WINDOW_MS) {
            log.debug(
                "HMAC 검증 실패: timestamp 윈도우 밖 (now={} ts={} diff={}ms)",
                now,
                timestamp,
                now - timestamp,
            )
            return false
        }

        val given = signatureHeader.substring(SIG_PREFIX.length)
        val expected = computeHexHmac(secret, timestamp, body)

        // hex 비교 — 길이 다르면 isEqual 가 false. timing-safe.
        return MessageDigest.isEqual(
            given.toByteArray(StandardCharsets.US_ASCII),
            expected.toByteArray(StandardCharsets.US_ASCII),
        )
    }

    /** 외부 도구 (테스트 / 운영자 디버깅) 가 같은 알고리즘으로 서명 만들 때 사용. */
    @JvmStatic
    fun computeHexHmac(secret: String, timestamp: Long, body: ByteArray): String {
        try {
            val mac = Mac.getInstance(ALGO)
            mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), ALGO))
            mac.update(timestamp.toString().toByteArray(StandardCharsets.UTF_8))
            mac.update('.'.code.toByte())
            mac.update(body)
            return toHex(mac.doFinal())
        } catch (e: Exception) {
            throw IllegalStateException("HMAC 계산 실패", e)
        }
    }

    private fun toHex(data: ByteArray): String {
        val sb = StringBuilder(data.size * 2)
        for (b in data) {
            sb.append(Character.forDigit((b.toInt() shr 4) and 0xF, 16))
            sb.append(Character.forDigit(b.toInt() and 0xF, 16))
        }
        return sb.toString()
    }
}
