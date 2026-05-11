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

    // cursor 유/무를 별도 쿼리로 분리. 단일 쿼리에 ":cursorTs IS NULL OR ..." 를 두면
    // PostgreSQL JDBC 가 NULL 파라미터의 타입을 추론 못 해서 500 (could not determine
    // data type of parameter) 으로 떨어진다. H2(PG mode) 에서는 통과해도 실제 PG 에서 깨짐.
    @Query(
            "SELECT n FROM NotificationEntity n WHERE n.recipientId = :recipientId "
                    + "ORDER BY n.createdAt DESC, n.id DESC")
    List<NotificationEntity> findHistoryFirstPage(
            @Param("recipientId") String recipientId, Pageable pageable);

    @Query(
            "SELECT n FROM NotificationEntity n WHERE n.recipientId = :recipientId "
                    + "AND n.createdAt < :cursorTs "
                    + "ORDER BY n.createdAt DESC, n.id DESC")
    List<NotificationEntity> findHistoryAfterCursor(
            @Param("recipientId") String recipientId,
            @Param("cursorTs") Instant cursorTs,
            Pageable pageable);
}
