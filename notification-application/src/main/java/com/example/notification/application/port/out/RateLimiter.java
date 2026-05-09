package com.example.notification.application.port.out;

import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.recipient.RecipientId;
import com.example.notification.domain.shared.RateLimitDecision;

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
}
