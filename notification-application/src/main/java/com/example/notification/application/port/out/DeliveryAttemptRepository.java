package com.example.notification.application.port.out;

import com.example.notification.domain.delivery.DeliveryAttempt;
import com.example.notification.domain.delivery.DeliveryStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** DeliveryAttempt persistence port. */
public interface DeliveryAttemptRepository {

    DeliveryAttempt save(DeliveryAttempt attempt);

    List<DeliveryAttempt> saveAll(List<DeliveryAttempt> attempts);

    Optional<DeliveryAttempt> findById(UUID id);

    List<DeliveryAttempt> findByNotificationId(UUID notificationId);

    /**
     * DLQ 운영용 cursor 페이지네이션. 정해진 status (보통 EXHAUSTED) 의 attempt 중 cursor 보다
     * id 가 큰 것을 createdAt 오름차순으로 N개. cursor 가 null 이면 처음부터.
     */
    List<DeliveryAttempt> findByStatusAfter(DeliveryStatus status, UUID cursor, int limit);
}
