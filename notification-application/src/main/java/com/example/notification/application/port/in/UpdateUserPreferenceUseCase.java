package com.example.notification.application.port.in;

import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.notification.NotificationKind;
import com.example.notification.domain.preference.QuietHours;
import com.example.notification.domain.preference.UserPreference;
import java.time.ZoneId;
import java.util.Set;

/**
 * 사용자 본인이 자기 채널 선호도 변경. 거래성/보안 알림은 mandatory 라 opt-out 불가
 * (도메인이 IllegalArgumentException 으로 거절).
 */
public interface UpdateUserPreferenceUseCase {

    UserPreference update(UpdateCommand command);

    /**
     * @param recipientId 사용자 id
     * @param kind 변경 대상 알림 종류 (null 이면 quiet hours 만 변경)
     * @param allowed null 이 아니면 해당 kind opt-in/out 변경
     * @param preferredChannels null 이 아니면 해당 kind 우선 채널 변경
     * @param quietHours null 이면 변경 안 함, "DISABLED" 같은 sentinel 은 따로 둠
     * @param disableQuietHours true 면 DND 비활성
     * @param timezone null 이면 변경 안 함
     */
    record UpdateCommand(
            String recipientId,
            NotificationKind kind,
            Boolean allowed,
            Set<ChannelType> preferredChannels,
            QuietHours quietHours,
            boolean disableQuietHours,
            ZoneId timezone) {}
}
