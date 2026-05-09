package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.entity.DeliveryAttemptEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptJpaRepository extends JpaRepository<DeliveryAttemptEntity, UUID> {
    List<DeliveryAttemptEntity> findByNotificationId(UUID notificationId);
}
