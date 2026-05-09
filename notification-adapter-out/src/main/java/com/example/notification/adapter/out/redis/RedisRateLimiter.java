package com.example.notification.adapter.out.redis;

import com.example.notification.application.port.out.RateLimiter;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.recipient.RecipientId;
import com.example.notification.domain.shared.RateLimitDecision;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * recipient × channel 별 token bucket. Redis 의 INCR + EXPIRE 조합 (Lua 스크립트로 원자) 으로
 * 분당 N개 처리.
 *
 * <p>운영에선 leaky bucket / sliding window 도 있지만 이 hub 는 단순 fixed-window 를 사용.
 * 1 초 단위 윈도우의 startover 직후 burst 가 가능한 단점 인지하고 사용 (ADR-0006 참고).
 *
 * <p>한도는 채널별 차등 — push/email 은 분당 30, sms/알림톡 은 분당 5 (vendor 비용 고려).
 */
@Component
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    static final String NAMESPACE = "notif:rl:";

    /** Lua 스크립트로 INCR + 첫 hit 일 때만 EXPIRE 설정. 두 명령 사이 race 방지. */
    private static final String LUA_INCR_AND_TTL =
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            return {current, ttl}
            """;

    private static final DefaultRedisScript<List> SCRIPT =
            new DefaultRedisScript<>(LUA_INCR_AND_TTL, List.class);

    private final StringRedisTemplate redis;

    @Value("${ratelimit.window-ms:60000}")
    private long windowMs;

    @Value("${ratelimit.push-per-window:30}")
    private long pushLimit;

    @Value("${ratelimit.email-per-window:30}")
    private long emailLimit;

    @Value("${ratelimit.sms-per-window:5}")
    private long smsLimit;

    @Value("${ratelimit.kakao-per-window:5}")
    private long kakaoLimit;

    @Override
    public RateLimitDecision tryConsume(RecipientId recipientId, ChannelType channel) {
        long limit = limitFor(channel);
        String key = NAMESPACE + channel.name() + ":" + recipientId.value();
        @SuppressWarnings("unchecked")
        List<Long> result =
                redis.execute(
                        SCRIPT,
                        java.util.Collections.singletonList(key),
                        String.valueOf(windowMs));
        long current = result.get(0);
        long ttl = result.get(1);
        if (current > limit) {
            return RateLimitDecision.deny(ttl);
        }
        return RateLimitDecision.allow(Math.max(0, limit - current));
    }

    private long limitFor(ChannelType channel) {
        return switch (channel) {
            case PUSH -> pushLimit;
            case EMAIL -> emailLimit;
            case SMS -> smsLimit;
            case KAKAO_ALIMTALK -> kakaoLimit;
        };
    }

    /** 테스트용 — 윈도우 강제 reset. */
    void reset(RecipientId recipientId, ChannelType channel) {
        redis.delete(NAMESPACE + channel.name() + ":" + recipientId.value());
    }

    /** Default 한도 (테스트에서 직접 사용). */
    public Duration window() {
        return Duration.ofMillis(windowMs);
    }
}
