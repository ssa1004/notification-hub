package com.example.notification.domain.shared

/**
 * Token bucket 결정 결과. recipient × channel 쌍별로 분당 N개 까지 허용 같은 정책에서 사용.
 *
 * @property allowed 이번 호출이 허용되는지
 * @property remainingTokens 남은 토큰 수 (디버그/메트릭)
 * @property retryAfterMillis allowed=false 일 때 다음 시도까지 대기 권장 시간 (ms)
 */
@JvmRecord
data class RateLimitDecision(
    val allowed: Boolean,
    val remainingTokens: Long,
    val retryAfterMillis: Long,
) {

    companion object {
        @JvmStatic
        fun allow(remaining: Long): RateLimitDecision = RateLimitDecision(true, remaining, 0)

        @JvmStatic
        fun deny(retryAfterMillis: Long): RateLimitDecision =
            RateLimitDecision(false, 0, retryAfterMillis)
    }
}
