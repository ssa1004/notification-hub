package com.example.notification.adapter.in.rest.dto;

import com.example.notification.application.dto.SendNotificationResult;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.notification.NotificationStatus;
import java.util.List;
import java.util.UUID;

public record SendNotificationResponse(
        UUID notificationId,
        NotificationStatus status,
        List<ChannelType> dispatchedChannels,
        String suppressionReason) {

    public static SendNotificationResponse from(SendNotificationResult result) {
        return new SendNotificationResponse(
                result.notificationId(),
                result.status(),
                result.dispatchedChannels(),
                result.suppressionReason());
    }
}
