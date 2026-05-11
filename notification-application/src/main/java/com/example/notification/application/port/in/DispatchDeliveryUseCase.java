package com.example.notification.application.port.in;

import java.util.UUID;

/**
 * Kafka consumer 가 호출하는 내부 use case. 한 PENDING attempt 를 vendor 로 발송 시도.
 *
 * <p>이 use case 는 외부 (사용자/운영자) 에 노출되지 않음 — adapter-in 의 Kafka consumer
 * 에서만 호출. REST endpoint 가 따로 없음.
 */
public interface DispatchDeliveryUseCase {

    void dispatch(UUID deliveryAttemptId);
}
