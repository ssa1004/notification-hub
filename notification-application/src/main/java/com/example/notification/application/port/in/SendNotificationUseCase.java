package com.example.notification.application.port.in;

import com.example.notification.application.dto.SendNotificationCommand;
import com.example.notification.application.dto.SendNotificationResult;

/**
 * 한 알림을 보낸다. 흐름:
 *
 * <ol>
 *   <li>Idempotency-Key 점유 (Redis SETNX)
 *   <li>Recipient + UserPreference 조회
 *   <li>opt-out / DND 체크 → SUPPRESSED 면 fan-out 생략
 *   <li>대상 채널 결정 + rate limit 적용
 *   <li>채널별 템플릿 렌더링 → DeliveryAttempt 생성 (PENDING)
 *   <li>Outbox 에 NotificationFannedOut + DeliveryRequested 적재 (DB tx 안)
 * </ol>
 *
 * <p>실제 vendor 호출은 채널별 worker (Kafka consumer) 가 PENDING attempt 를 가져가 실행.
 */
public interface SendNotificationUseCase {

    SendNotificationResult send(SendNotificationCommand command);
}
