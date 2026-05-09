package com.example.notification.domain.shared;

/**
 * Token bucket 결정 결과. recipient × channel 쌍별로 분당 N개 까지 허용 같은 정책에서 사용.
 *
 * @param allowed       이번 호출이 허용되는지
 * @param remainingTokens 남은 토큰 수 (디버그/메트릭)
 * @param retryAfterMillis allowed=false 일 때 다음 시도까지 대기 권장 시간 (ms)
 */
public record RateLimitDecision(boolean allowed, long remainingTokens, long retryAfterMillis) {

    public static RateLimitDecision allow(long remaining) {
        return new RateLimitDecision(true, remaining, 0);
    }

    public static RateLimitDecision deny(long retryAfterMillis) {
        return new RateLimitDecision(false, 0, retryAfterMillis);
    }
}
