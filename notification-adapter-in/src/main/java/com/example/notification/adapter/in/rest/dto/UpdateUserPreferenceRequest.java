package com.example.notification.adapter.in.rest.dto;

import com.example.notification.application.port.in.UpdateUserPreferenceUseCase.UpdateCommand;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.notification.NotificationKind;
import com.example.notification.domain.preference.QuietHours;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

public record UpdateUserPreferenceRequest(
        NotificationKind kind,
        Boolean allowed,
        Set<ChannelType> preferredChannels,
        String quietStart,  // "22:00"
        String quietEnd,    // "08:00"
        Boolean disableQuietHours,
        String timezone     // "Asia/Seoul"
        ) {

    public UpdateCommand toCommand(String recipientId) {
        QuietHours qh = null;
        if (quietStart != null && quietEnd != null) {
            qh = new QuietHours(LocalTime.parse(quietStart), LocalTime.parse(quietEnd));
        }
        return new UpdateCommand(
                recipientId,
                kind,
                allowed,
                preferredChannels,
                qh,
                Boolean.TRUE.equals(disableQuietHours),
                timezone == null ? null : ZoneId.of(timezone));
    }
}
