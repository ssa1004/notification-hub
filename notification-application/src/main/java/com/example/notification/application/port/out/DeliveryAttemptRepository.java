package com.example.notification.application.port.out;

import com.example.notification.domain.delivery.DeliveryAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** DeliveryAttempt persistence port. */
public interface DeliveryAttemptRepository {

    DeliveryAttempt save(DeliveryAttempt attempt);

    List<DeliveryAttempt> saveAll(List<DeliveryAttempt> attempts);

    Optional<DeliveryAttempt> findById(UUID id);

    List<DeliveryAttempt> findByNotificationId(UUID notificationId);
}
