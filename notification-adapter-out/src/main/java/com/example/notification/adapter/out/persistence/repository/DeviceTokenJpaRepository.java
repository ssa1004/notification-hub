package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.entity.DeviceTokenEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenJpaRepository extends JpaRepository<DeviceTokenEntity, UUID> {

    Optional<DeviceTokenEntity> findByToken(String token);

    List<DeviceTokenEntity> findByRecipientIdAndDisabledAtIsNull(String recipientId);
}
