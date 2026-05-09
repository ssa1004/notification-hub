package com.example.notification.adapter.out.persistence;

import com.example.notification.adapter.out.persistence.mapper.DeliveryAttemptMapper;
import com.example.notification.adapter.out.persistence.repository.DeliveryAttemptJpaRepository;
import com.example.notification.application.port.out.DeliveryAttemptRepository;
import com.example.notification.domain.delivery.DeliveryAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaDeliveryAttemptRepository implements DeliveryAttemptRepository {

    private final DeliveryAttemptJpaRepository jpa;

    @Override
    public DeliveryAttempt save(DeliveryAttempt attempt) {
        return DeliveryAttemptMapper.toDomain(jpa.save(DeliveryAttemptMapper.toEntity(attempt)));
    }

    @Override
    public List<DeliveryAttempt> saveAll(List<DeliveryAttempt> attempts) {
        return jpa.saveAll(attempts.stream().map(DeliveryAttemptMapper::toEntity).toList())
                .stream()
                .map(DeliveryAttemptMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<DeliveryAttempt> findById(UUID id) {
        return jpa.findById(id).map(DeliveryAttemptMapper::toDomain);
    }

    @Override
    public List<DeliveryAttempt> findByNotificationId(UUID notificationId) {
        return jpa.findByNotificationId(notificationId).stream()
                .map(DeliveryAttemptMapper::toDomain)
                .toList();
    }
}
