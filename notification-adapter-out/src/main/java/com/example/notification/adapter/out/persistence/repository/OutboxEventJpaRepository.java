package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.entity.OutboxEventEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {

    @Query(
            "SELECT o FROM OutboxEventEntity o WHERE o.status = :status "
                    + "ORDER BY o.id ASC")
    List<OutboxEventEntity> findPending(@Param("status") String status, Pageable pageable);
}
