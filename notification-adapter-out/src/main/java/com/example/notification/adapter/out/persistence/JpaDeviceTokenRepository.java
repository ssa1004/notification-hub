package com.example.notification.adapter.out.persistence;

import com.example.notification.adapter.out.persistence.mapper.DeviceTokenMapper;
import com.example.notification.adapter.out.persistence.repository.DeviceTokenJpaRepository;
import com.example.notification.application.port.out.DeviceTokenRepository;
import com.example.notification.domain.device.DeviceToken;
import com.example.notification.domain.recipient.RecipientId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaDeviceTokenRepository implements DeviceTokenRepository {

    private final DeviceTokenJpaRepository jpa;

    @Override
    public DeviceToken save(DeviceToken token) {
        return DeviceTokenMapper.toDomain(jpa.save(DeviceTokenMapper.toEntity(token)));
    }

    @Override
    public Optional<DeviceToken> findById(UUID id) {
        return jpa.findById(id).map(DeviceTokenMapper::toDomain);
    }

    @Override
    public List<DeviceToken> findActiveByRecipientId(RecipientId recipientId) {
        return jpa.findByRecipientIdAndDisabledAtIsNull(recipientId.value()).stream()
                .map(DeviceTokenMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<DeviceToken> findByToken(String token) {
        return jpa.findByToken(token).map(DeviceTokenMapper::toDomain);
    }
}
