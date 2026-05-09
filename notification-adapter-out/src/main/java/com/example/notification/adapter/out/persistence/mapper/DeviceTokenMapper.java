package com.example.notification.adapter.out.persistence.mapper;

import com.example.notification.adapter.out.persistence.entity.DeviceTokenEntity;
import com.example.notification.domain.device.DeviceToken;
import com.example.notification.domain.recipient.RecipientId;

public final class DeviceTokenMapper {

    private DeviceTokenMapper() {}

    public static DeviceTokenEntity toEntity(DeviceToken d) {
        DeviceTokenEntity e = new DeviceTokenEntity();
        e.setId(d.id());
        e.setRecipientId(d.recipientId().value());
        e.setPlatform(d.platform());
        e.setToken(d.token());
        e.setRegisteredAt(d.registeredAt());
        e.setDisabledAt(d.disabledAt());
        return e;
    }

    public static DeviceToken toDomain(DeviceTokenEntity e) {
        return new DeviceToken(
                e.getId(),
                new RecipientId(e.getRecipientId()),
                e.getPlatform(),
                e.getToken(),
                e.getRegisteredAt(),
                e.getDisabledAt());
    }
}
