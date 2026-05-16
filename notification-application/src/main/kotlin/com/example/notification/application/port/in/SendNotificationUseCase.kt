package com.example.notification.application.port.`in`

import com.example.notification.application.dto.SendNotificationCommand
import com.example.notification.application.dto.SendNotificationResult

/**
 * 한 알림을 보낸다. 흐름:
 *
 * 1. Idempotency-Key 점유 (Redis SETNX)
 * 2. Recipient + UserPreference 조회
 * 3. opt-out / DND 체크 → SUPPRESSED 면 fan-out 생략
 * 4. 대상 채널 결정 + rate limit 적용
 * 5. 채널별 템플릿 렌더링 → DeliveryAttempt 생성 (PENDING)
 * 6. Outbox 에 NotificationFannedOut + DeliveryRequested 적재 (DB tx 안)
 *
 * 실제 vendor 호출은 채널별 worker (Kafka consumer) 가 PENDING attempt 를 가져가 실행.
 */
interface SendNotificationUseCase {

    fun send(command: SendNotificationCommand): SendNotificationResult
}
