package com.example.notification.application.exception

/** Rate limit 초과 — 사용자별 채널별 토큰 소진. HTTP 429. */
class RateLimitExceededException(
    channel: String,
    retryAfterMillis: Long,
) : ApplicationException("rate limit exceeded for channel=$channel retryAfterMs=$retryAfterMillis") {

    @get:JvmName("retryAfterMillis")
    val retryAfterMillis: Long = retryAfterMillis
}
