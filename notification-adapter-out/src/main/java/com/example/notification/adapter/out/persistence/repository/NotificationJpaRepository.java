package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.entity.NotificationEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {

    @Query(
            "SELECT n FROM NotificationEntity n WHERE n.recipientId = :recipientId "
                    + "AND (:cursorTs IS NULL OR n.createdAt < :cursorTs) "
                    + "ORDER BY n.createdAt DESC, n.id DESC")
    List<NotificationEntity> findHistory(
            @Param("recipientId") String recipientId,
            @Param("cursorTs") Instant cursorTs,
            Pageable pageable);
}
