package com.example.notification.application.exception;

/** Rate limit 초과 — 사용자별 채널별 토큰 소진. HTTP 429. */
public class RateLimitExceededException extends ApplicationException {

    private final long retryAfterMillis;

    public RateLimitExceededException(String channel, long retryAfterMillis) {
        super("rate limit exceeded for channel=" + channel
                + " retryAfterMs=" + retryAfterMillis);
        this.retryAfterMillis = retryAfterMillis;
    }

    public long retryAfterMillis() {
        return retryAfterMillis;
    }
}
