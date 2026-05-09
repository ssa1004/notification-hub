package com.example.notification.adapter.out.persistence;

import com.example.notification.adapter.out.persistence.entity.NotificationEntity;
import com.example.notification.adapter.out.persistence.mapper.NotificationMapper;
import com.example.notification.adapter.out.persistence.repository.NotificationJpaRepository;
import com.example.notification.application.dto.DeliveryHistoryPage;
import com.example.notification.application.port.out.NotificationRepository;
import com.example.notification.domain.notification.Notification;
import com.example.notification.domain.recipient.RecipientId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaNotificationRepository implements NotificationRepository {

    private final NotificationJpaRepository jpa;

    @Override
    public Notification save(Notification notification) {
        NotificationEntity saved = jpa.save(NotificationMapper.toEntity(notification));
        return NotificationMapper.toDomain(saved);
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jpa.findById(id).map(NotificationMapper::toDomain);
    }

    @Override
    public DeliveryHistoryPage findHistory(RecipientId recipientId, UUID cursor, int limit) {
        Instant cursorTs = null;
        if (cursor != null) {
            cursorTs = jpa.findById(cursor)
                    .map(NotificationEntity::getCreatedAt)
                    .orElse(null);
        }
        // limit + 1 만 fetch 해서 다음 페이지 존재 여부 판단
        List<NotificationEntity> rows = jpa.findHistory(
                recipientId.value(), cursorTs, PageRequest.of(0, limit + 1));
        boolean hasMore = rows.size() > limit;
        List<NotificationEntity> page = hasMore ? rows.subList(0, limit) : rows;

        List<DeliveryHistoryPage.Item> items = page.stream()
                .map(e -> new DeliveryHistoryPage.Item(
                        e.getId(),
                        e.getTitle(),
                        e.getKind().name(),
                        e.getStatus().name(),
                        e.getCreatedAt()))
                .toList();
        UUID nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
        return new DeliveryHistoryPage(items, nextCursor);
    }
}
