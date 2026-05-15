package com.example.notification.domain.notification

/**
 * 알림의 비즈니스 분류. 채널 선호도 / opt-out / DND 적용 여부를 결정.
 *
 * - [MARKETING] — 광고/프로모션. 사용자 opt-out 가능, DND 적용, 야간 차단.
 * - [TRANSACTIONAL] — 결제/주문 등 거래 확인. opt-out 불가, DND 적용.
 * - [SECURITY] — OTP, 로그인 알림, 사기 탐지. opt-out 불가, DND 우회.
 * - [SERVICE] — 서비스 공지/업데이트. opt-out 가능, DND 적용.
 */
enum class NotificationKind(
    private val mandatory: Boolean,
    private val respectsQuietHours: Boolean,
    private val bypassesQuietHours: Boolean,
) {

    MARKETING(false, true, false),
    TRANSACTIONAL(true, true, false),
    SECURITY(true, false, true),
    SERVICE(false, true, false);

    /** 사용자가 opt-out 할 수 없는 종류인가? */
    fun mandatory(): Boolean = mandatory

    /** DND 시간대에 차단되어야 하는가? */
    fun respectsQuietHours(): Boolean = respectsQuietHours && !bypassesQuietHours

    /** DND 를 무조건 우회해서 보내야 하는가? (보안 알림) */
    fun bypassesQuietHours(): Boolean = bypassesQuietHours
}
