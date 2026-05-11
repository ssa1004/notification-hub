package com.example.notification.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.notification.application.port.out.RateLimiter;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.recipient.RecipientId;
import com.example.notification.domain.shared.RateLimitDecision;
import com.redis.testcontainers.RedisContainer;
import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.utility.DockerImageName;

/**
 * RedisRateLimiter 의 원자 batch 차감 회귀 가드 — Testcontainers Redis 사용. 가장 중요한 보장:
 * 다채널 묶음에서 한 채널이라도 토큰 부족이면, 다른 채널의 토큰도 차감되지 않아야 한다.
 * 옛 per-channel tryConsume 흐름은 channel#1 은 차감 + channel#2 거절 → channel#1 token leak.
 */
class RedisRateLimiterTest {

    static RedisContainer redis;
    static LettuceConnectionFactory factory;
    static StringRedisTemplate template;

    RateLimiter sut;
    RecipientId user;

    @BeforeAll
    static void start() {
        redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));
        redis.start();
        factory =
                new LettuceConnectionFactory(redis.getRedisHost(), redis.getRedisPort());
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
    }

    @AfterAll
    static void stop() {
        if (factory != null) {
            factory.destroy();
        }
        if (redis != null) {
            redis.stop();
        }
    }

    @BeforeEach
    void setUp() {
        // 매 테스트 키 격리 위해 user id 다르게.
        user = new RecipientId("u-" + System.nanoTime());
        RedisRateLimiter impl = new RedisRateLimiter(template);
        // 채널별 한도: PUSH=2, EMAIL=2, SMS=2, KAKAO=2 (작게 잡아 시나리오 단순화).
        setLong(impl, "windowMs", 60_000);
        setLong(impl, "pushLimit", 2);
        setLong(impl, "emailLimit", 2);
        setLong(impl, "smsLimit", 2);
        setLong(impl, "kakaoLimit", 2);
        sut = impl;
    }

    @Test
    void batch_모든_채널_여유_있으면_일괄_차감() {
        Map<ChannelType, Integer> demand = new EnumMap<>(ChannelType.class);
        demand.put(ChannelType.PUSH, 1);
        demand.put(ChannelType.EMAIL, 1);

        Map<ChannelType, RateLimitDecision> result = sut.tryConsumeAll(user, demand);

        assertThat(result.values()).allSatisfy(d -> assertThat(d.allowed()).isTrue());
        // 두 번째 호출 — 각 1 추가 → 2/2 가 되어 아직 통과.
        Map<ChannelType, RateLimitDecision> r2 = sut.tryConsumeAll(user, demand);
        assertThat(r2.values()).allSatisfy(d -> assertThat(d.allowed()).isTrue());
    }

    /**
     * 핵심 회귀 락 — 한 채널 부족이면 다른 채널은 차감 안 됨. 이 보장이 깨지면 multi-channel
     * 알림이 전부 거절되지만 한쪽 채널 토큰은 빠져나가는 leak.
     */
    @Test
    void batch_한_채널_부족이면_다른_채널_토큰_보존() {
        // EMAIL 을 한도까지 채움 (limit=2 이므로 2번 차감)
        Map<ChannelType, Integer> emailOnly =
                new EnumMap<>(Map.of(ChannelType.EMAIL, 2));
        Map<ChannelType, RateLimitDecision> r1 = sut.tryConsumeAll(user, emailOnly);
        assertThat(r1.get(ChannelType.EMAIL).allowed()).isTrue();

        // 이제 PUSH+EMAIL 묶음 시도. EMAIL 은 이미 2/2 → 1 demand 추가하면 3 > 2 → 거절.
        // 핵심: 거절되었으니 PUSH 는 차감 안 되어야 한다.
        Map<ChannelType, Integer> mixed = new EnumMap<>(ChannelType.class);
        mixed.put(ChannelType.PUSH, 1);
        mixed.put(ChannelType.EMAIL, 1);
        Map<ChannelType, RateLimitDecision> r2 = sut.tryConsumeAll(user, mixed);

        // EMAIL 은 거절, PUSH 도 묶음 의미 보존을 위해 거절.
        assertThat(r2.get(ChannelType.EMAIL).allowed()).isFalse();
        assertThat(r2.get(ChannelType.PUSH).allowed()).isFalse();

        // 이제 PUSH 단독으로 한도 (2) 까지 모두 통과 가능해야 한다 — 이전 mixed 호출에서 PUSH
        // 가 차감되었다면 1번만 통과하고 다음에 거절될 것이다.
        Map<ChannelType, Integer> pushOnly = new EnumMap<>(Map.of(ChannelType.PUSH, 2));
        Map<ChannelType, RateLimitDecision> r3 = sut.tryConsumeAll(user, pushOnly);
        assertThat(r3.get(ChannelType.PUSH).allowed()).isTrue();
    }

    @Test
    void batch_PUSH_multi_device_demand_도_원자() {
        // PUSH 한도 2 — demand=3 이면 (0+3) > 2 → 즉시 거절.
        Map<ChannelType, Integer> push3 = new EnumMap<>(Map.of(ChannelType.PUSH, 3));
        Map<ChannelType, RateLimitDecision> r1 = sut.tryConsumeAll(user, push3);
        assertThat(r1.get(ChannelType.PUSH).allowed()).isFalse();

        // demand=2 면 통과.
        Map<ChannelType, Integer> push2 = new EnumMap<>(Map.of(ChannelType.PUSH, 2));
        Map<ChannelType, RateLimitDecision> r2 = sut.tryConsumeAll(user, push2);
        assertThat(r2.get(ChannelType.PUSH).allowed()).isTrue();

        // 한도 차서 다음 1 demand 도 거절.
        Map<ChannelType, Integer> push1 = new EnumMap<>(Map.of(ChannelType.PUSH, 1));
        Map<ChannelType, RateLimitDecision> r3 = sut.tryConsumeAll(user, push1);
        assertThat(r3.get(ChannelType.PUSH).allowed()).isFalse();
    }

    @Test
    void batch_demand_가_0_이하면_검증_실패() {
        Map<ChannelType, Integer> bad = new EnumMap<>(Map.of(ChannelType.PUSH, 0));
        try {
            sut.tryConsumeAll(user, bad);
            org.junit.jupiter.api.Assertions.fail("expected IAE");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    void single_tryConsume_도_그대로_동작() {
        RateLimitDecision d1 = sut.tryConsume(user, ChannelType.PUSH);
        assertThat(d1.allowed()).isTrue();
        RateLimitDecision d2 = sut.tryConsume(user, ChannelType.PUSH);
        assertThat(d2.allowed()).isTrue();
        // 한도 2 초과 → 거절
        RateLimitDecision d3 = sut.tryConsume(user, ChannelType.PUSH);
        assertThat(d3.allowed()).isFalse();
    }

    private static void setLong(Object target, String name, long value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.setLong(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
