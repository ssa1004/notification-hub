package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.entity.DeliveryAttemptEntity;
import com.example.notification.domain.delivery.DeliveryStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptJpaRepository extends JpaRepository<DeliveryAttemptEntity, UUID> {
    List<DeliveryAttemptEntity> findByNotificationId(UUID notificationId);

    /** DLQ cursor 페이지네이션 — id 가 cursor 보다 큰 항목만 N개. */
    List<DeliveryAttemptEntity> findByStatusAndIdGreaterThanOrderByIdAsc(
            DeliveryStatus status, UUID cursor, Pageable pageable);
}
