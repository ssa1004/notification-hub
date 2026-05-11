package com.example.notification.application.port.out;

import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.recipient.RecipientId;
import com.example.notification.domain.shared.RateLimitDecision;
import java.util.Map;

/**
 * recipient × channel 별 token bucket. 같은 사용자에게 쏟아지는 알림 폭주를 막기 위한 장치.
 *
 * <p>예: 같은 사용자 같은 채널 분당 10개. 11번째는 deny → use case 가 차단 결정.
 *
 * <p>ADR-0006 — vendor 비용 / 사용자 경험 / 스팸 신고 방지 모두 걸려 있음.
 */
public interface RateLimiter {

    /** 토큰 1개 시도. */
    RateLimitDecision tryConsume(RecipientId recipientId, ChannelType channel);

    /**
     * 여러 채널의 토큰을 원자적으로 시도. 한 묶음 알림이 PUSH+EMAIL 같이 다채널 fan-out 일 때,
     * 채널별로 따로 {@link #tryConsume} 를 호출하면 channel#1 은 통과 (토큰 차감) 후 channel#2
     * 가 거절되는 케이스에서 channel#1 토큰만 부분 소진 — 사용자 입장에서는 실제 발송이 0건인데
     * 토큰만 빠진 leak.
     *
     * <p>구현체는 모든 채널의 가용성을 먼저 확인 후, 전부 가능할 때만 일괄 차감해야 한다 (Redis
     * Lua 스크립트로 보장). 어느 하나라도 차단되면 모든 채널의 토큰이 그대로 유지되고 결과 맵의
     * 거절 채널마다 {@link RateLimitDecision#allowed()} = false 로 마킹.
     *
     * @param demand 채널 → 이번 묶음에서 차감할 토큰 개수 (PUSH multi-device fan-out 시 device
     *               수만큼). 0 이하 값은 허용 X.
     * @return 입력 demand 의 모든 키에 대한 결과 — 모두 allowed=true 거나, 차단된 채널 결정이
     *         하나 이상 포함.
     */
    Map<ChannelType, RateLimitDecision> tryConsumeAll(
            RecipientId recipientId, Map<ChannelType, Integer> demand);
}
