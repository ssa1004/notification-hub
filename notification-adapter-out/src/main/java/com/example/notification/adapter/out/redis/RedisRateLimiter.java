package com.example.notification.adapter.out.redis;

import com.example.notification.application.port.out.RateLimiter;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.recipient.RecipientId;
import com.example.notification.domain.shared.RateLimitDecision;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    /**
     * 여러 키 원자 차감. ARGV[1] = windowMs, ARGV[2..N+1] = limit_i, ARGV[N+2..2N+1] = demand_i.
     * 1) 모든 키의 current 를 읽어 (current+demand) > limit 가 하나라도 있으면 INCRBY 안 함.
     * 2) 가능하면 일괄 INCRBY + 첫 hit 키에만 PEXPIRE.
     * 반환: {state, current_1, ttl_1, current_2, ttl_2, ...} — state=1 이면 모두 통과,
     * state=0 이면 모두 거절 (이때 current_i 는 INCRBY 전 값, 즉 그대로 유지된 값).
     */
    private static final String LUA_BATCH_TRY_CONSUME =
            """
            local n = #KEYS
            local windowMs = ARGV[1]
            local denied = false
            local currents = {}
            for i = 1, n do
              local cur = tonumber(redis.call('GET', KEYS[i])) or 0
              local limit = tonumber(ARGV[1 + i])
              local demand = tonumber(ARGV[1 + n + i])
              currents[i] = cur
              if cur + demand > limit then
                denied = true
              end
            end
            local result = {}
            if denied then
              result[1] = 0
              for i = 1, n do
                local ttl = tonumber(redis.call('PTTL', KEYS[i]))
                if ttl == nil or ttl < 0 then ttl = -1 end
                result[#result+1] = currents[i]
                result[#result+1] = ttl
              end
              return result
            end
            result[1] = 1
            for i = 1, n do
              local demand = tonumber(ARGV[1 + n + i])
              local newval = redis.call('INCRBY', KEYS[i], demand)
              if currents[i] == 0 then
                redis.call('PEXPIRE', KEYS[i], windowMs)
              end
              local ttl = tonumber(redis.call('PTTL', KEYS[i]))
              if ttl == nil or ttl < 0 then ttl = -1 end
              result[#result+1] = newval
              result[#result+1] = ttl
            end
            return result
            """;

    // Spring 의 DefaultRedisScript 는 result type 으로 raw List 를 기대 — element 타입이 Lua
    // 반환 구조에 따라 달라지므로 generic 으로 좁히지 못한다. raw 사용은 명시적으로 suppress.
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> SCRIPT =
            new DefaultRedisScript<>(LUA_INCR_AND_TTL, List.class);

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> BATCH_SCRIPT =
            new DefaultRedisScript<>(LUA_BATCH_TRY_CONSUME, List.class);

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

    @Override
    public Map<ChannelType, RateLimitDecision> tryConsumeAll(
            RecipientId recipientId, Map<ChannelType, Integer> demand) {
        if (demand == null || demand.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        // 입력 검증: demand 값은 양수여야 함. 0/음수면 호출자가 잘못 만든 것.
        for (Map.Entry<ChannelType, Integer> e : demand.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) {
                throw new IllegalArgumentException(
                        "demand must be positive for " + e.getKey() + ": got " + e.getValue());
            }
        }

        // 결정성을 위해 ChannelType 순서 (enum 정의 순) 로 정렬해 KEYS / ARGV 작성.
        List<ChannelType> ordered = new ArrayList<>();
        for (ChannelType t : ChannelType.values()) {
            if (demand.containsKey(t)) {
                ordered.add(t);
            }
        }
        List<String> keys = new ArrayList<>(ordered.size());
        List<String> args = new ArrayList<>(1 + ordered.size() * 2);
        args.add(String.valueOf(windowMs));
        for (ChannelType t : ordered) {
            keys.add(NAMESPACE + t.name() + ":" + recipientId.value());
        }
        // limits 먼저, demands 다음 (Lua 스크립트의 인덱싱 규약).
        for (ChannelType t : ordered) {
            args.add(String.valueOf(limitFor(t)));
        }
        for (ChannelType t : ordered) {
            args.add(String.valueOf(demand.get(t)));
        }

        @SuppressWarnings("unchecked")
        List<Long> raw = redis.execute(BATCH_SCRIPT, keys, args.toArray());
        long state = raw.get(0);
        Map<ChannelType, RateLimitDecision> result = new EnumMap<>(ChannelType.class);
        for (int i = 0; i < ordered.size(); i++) {
            ChannelType t = ordered.get(i);
            long current = raw.get(1 + i * 2);
            long ttl = raw.get(2 + i * 2);
            long limit = limitFor(t);
            if (state == 0L) {
                // 모두 거절 — 차단된 채널은 retryAfter 로 ttl 노출. 통과 가능했던 채널도 의미 보존
                // 위해 deny 로 표기 (호출자는 어차피 묶음 재시도해야 함).
                long retryAfter = ttl >= 0 ? ttl : windowMs;
                if (current + demand.get(t) > limit) {
                    result.put(t, RateLimitDecision.deny(retryAfter));
                } else {
                    // 이 채널은 자체로는 통과 가능했지만 다른 채널 거절로 묶음 차감 보류.
                    // 호출자가 묶음 단위로 재시도하므로 deny 로 통일.
                    result.put(t, RateLimitDecision.deny(retryAfter));
                }
            } else {
                result.put(t, RateLimitDecision.allow(Math.max(0, limit - current)));
            }
        }
        return result;
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
