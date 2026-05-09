package com.example.notification.adapter.out.persistence.mapper;

import com.example.notification.adapter.out.persistence.entity.DeliveryAttemptEntity;
import com.example.notification.domain.channel.Channel;
import com.example.notification.domain.delivery.DeliveryAttempt;

public final class DeliveryAttemptMapper {

    private DeliveryAttemptMapper() {}

    public static DeliveryAttemptEntity toEntity(DeliveryAttempt a) {
        DeliveryAttemptEntity e = new DeliveryAttemptEntity();
        e.setId(a.id());
        e.setNotificationId(a.notificationId());
        e.setChannelType(a.channel().type());
        e.setChannelAddress(a.channel().address());
        e.setRenderedTitle(a.renderedTitle());
        e.setRenderedBody(a.renderedBody());
        e.setStatus(a.status());
        e.setRetryCount(a.retryCount());
        e.setNextAttemptAt(a.nextAttemptAt());
        e.setCompletedAt(a.completedAt());
        e.setVendorMessageId(a.vendorMessageId());
        e.setFailureReason(a.failureReason());
        e.setCreatedAt(a.createdAt());
        return e;
    }

    public static DeliveryAttempt toDomain(DeliveryAttemptEntity e) {
        return new DeliveryAttempt(
                e.getId(),
                e.getNotificationId(),
                new Channel(e.getChannelType(), e.getChannelAddress()),
                e.getRenderedTitle(),
                e.getRenderedBody(),
                e.getCreatedAt(),
                e.getStatus(),
                e.getRetryCount(),
                e.getNextAttemptAt(),
                e.getCompletedAt(),
                e.getVendorMessageId(),
                e.getFailureReason());
    }
}
