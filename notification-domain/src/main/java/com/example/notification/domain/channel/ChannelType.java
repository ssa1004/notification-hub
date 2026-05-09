package com.example.notification.domain.channel;

/**
 * 알림 발송 채널의 종류. 채널마다 vendor / 가격 / 즉시성 / 도착률이 다릅니다.
 *
 * <ul>
 *   <li>{@link #PUSH} — FCM/APNs 모바일 푸시. 즉시성 높음, opt-out 단순.
 *   <li>{@link #EMAIL} — SES/SendGrid. 비용 저렴, 도착 지연 가능.
 *   <li>{@link #SMS} — Twilio/카카오 SMS. 비용 비쌈, 글자 수 제한 (90B), 거의 100% 도착.
 *   <li>{@link #KAKAO_ALIMTALK} — 카카오 비즈메시지. 한국 한정, 사전 등록된 템플릿만, 야간 발송 제한.
 * </ul>
 *
 * <p>새 채널 추가 시: enum 추가 → 해당 vendor 의 {@code DeliveryGateway} 구현 작성 →
 * Outbox 의 channel routing 에 등록.
 */
public enum ChannelType {

    PUSH,
    EMAIL,
    SMS,
    KAKAO_ALIMTALK;

    /**
     * 야간 (22:00~08:00) 에 발송이 허용되는 채널인가? KAKAO_ALIMTALK 은 카카오 정책상 야간 발송
     * 금지 (광고성 메시지로 간주). PUSH/EMAIL/SMS 도 사용자 DND 설정에 의해 따로 차단됩니다.
     */
    public boolean allowedAtNight() {
        return this != KAKAO_ALIMTALK;
    }
}
