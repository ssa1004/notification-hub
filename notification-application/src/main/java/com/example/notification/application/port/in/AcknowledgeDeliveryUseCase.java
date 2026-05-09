package com.example.notification.application.port.in;

import java.util.UUID;

/**
 * vendor 의 비동기 콜백을 처리. vendor 호출 자체는 동기로 200 받았어도, *실제 도착 여부* 는
 * 별도 webhook 으로 알려줄 때 (예: SES delivery notification, FCM 콜백) 사용.
 *
 * <p>DLQ 에서도 같은 흐름을 사용 — 운영자 수동 ack 로 EXHAUSTED → SUCCEEDED 전환 가능.
 */
public interface AcknowledgeDeliveryUseCase {

    void acknowledge(AcknowledgeCommand command);

    /**
     * @param deliveryAttemptId 우리 시스템의 attempt id
     * @param success true 면 SUCCEEDED, false 면 FAILED 처리
     * @param vendorMessageId vendor 의 외부 id (성공 시)
     * @param failureReason 실패 사유 (실패 시)
     */
    record AcknowledgeCommand(
            UUID deliveryAttemptId,
            boolean success,
            String vendorMessageId,
            String failureReason) {}
}
