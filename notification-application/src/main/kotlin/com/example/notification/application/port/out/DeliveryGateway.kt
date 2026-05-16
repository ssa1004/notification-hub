package com.example.notification.application.port.out

import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.delivery.DeliveryAttempt

/**
 * 외부 vendor 호출 추상. 채널별 구현체:
 *
 * - [ChannelType.PUSH] — FcmDeliveryGateway
 * - [ChannelType.EMAIL] — SesDeliveryGateway
 * - [ChannelType.SMS] — TwilioDeliveryGateway
 * - [ChannelType.KAKAO_ALIMTALK] — KakaoAlimTalkDeliveryGateway
 *
 * 이 hub 자체는 vendor SDK 직접 의존을 피하고 mock client 만 두며 (학습 목적), 실제 운영
 * 환경에선 SDK + 인증 토큰 / endpoint 만 갈아끼우면 동작.
 */
interface DeliveryGateway {

    fun channelType(): ChannelType

    /**
     * 동기 호출. 성공 시 vendor 의 message id 반환. 실패 시 예외 던짐 → use case 가
     * [DeliveryAttempt.markFailed] 처리.
     */
    fun dispatch(attempt: DeliveryAttempt): String
}
