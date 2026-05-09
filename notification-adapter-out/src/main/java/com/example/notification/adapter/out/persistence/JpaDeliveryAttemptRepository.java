package com.example.notification.adapter.out.persistence;

import com.example.notification.adapter.out.persistence.mapper.DeliveryAttemptMapper;
import com.example.notification.adapter.out.persistence.repository.DeliveryAttemptJpaRepository;
import com.example.notification.application.port.out.DeliveryAttemptRepository;
import com.example.notification.domain.delivery.DeliveryAttempt;
import com.example.notification.domain.delivery.DeliveryStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

    @Override
    public List<DeliveryAttempt> findByStatusAfter(DeliveryStatus status, UUID cursor, int limit) {
        // cursor 페이지네이션 — id 오름차순. cursor null 이면 0..로 간주.
        UUID cursorEffective = cursor == null ? new UUID(0L, 0L) : cursor;
        return jpa.findByStatusAndIdGreaterThanOrderByIdAsc(
                        status, cursorEffective, PageRequest.of(0, limit))
                .stream()
                .map(DeliveryAttemptMapper::toDomain)
                .toList();
    }
}
