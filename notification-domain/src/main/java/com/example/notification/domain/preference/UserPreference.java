package com.example.notification.domain.preference;

import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.notification.NotificationKind;
import com.example.notification.domain.recipient.RecipientId;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 사용자별 알림 선호도. 한 알림이 들어왔을 때 어느 채널로 보낼지를 최종 결정.
 *
 * <p>구성 요소:
 * <ul>
 *   <li>{@code allowedByKind} — 알림 종류별 (마케팅 / 거래 / 공지) opt-out
 *   <li>{@code preferredChannels} — 알림 종류별 우선 사용 채널 (없으면 기본 정책)
 *   <li>{@code quietHours} — 방해금지 시간 (null 이면 비활성)
 *   <li>{@code timezone} — 위 시간 해석 기준
 * </ul>
 *
 * <p>거래성 (TRANSACTIONAL) / 보안성 (SECURITY) 알림은 사용자가 opt-out 못합니다 — 법적 의무
 * (예: OTP, 결제 알림) 와 사기 방지. {@link NotificationKind#mandatory()} 가 true 면 무시.
 */
public final class UserPreference {

    private final RecipientId recipientId;
    private final Map<NotificationKind, Boolean> allowedByKind;
    private final Map<NotificationKind, Set<ChannelType>> preferredChannels;
    private final QuietHours quietHours;
    private final ZoneId timezone;

    public UserPreference(
            RecipientId recipientId,
            Map<NotificationKind, Boolean> allowedByKind,
            Map<NotificationKind, Set<ChannelType>> preferredChannels,
            QuietHours quietHours,
            ZoneId timezone) {
        this.recipientId = Objects.requireNonNull(recipientId);
        this.allowedByKind = new EnumMap<>(NotificationKind.class);
        if (allowedByKind != null) this.allowedByKind.putAll(allowedByKind);
        this.preferredChannels = new EnumMap<>(NotificationKind.class);
        if (preferredChannels != null) {
            preferredChannels.forEach(
                    (k, v) -> this.preferredChannels.put(k, EnumSet.copyOf(v)));
        }
        this.quietHours = quietHours;
        this.timezone = Objects.requireNonNullElse(timezone, ZoneId.of("Asia/Seoul"));
    }

    public static UserPreference defaults(RecipientId recipientId) {
        return new UserPreference(
                recipientId,
                Map.of(),
                Map.of(),
                QuietHours.DEFAULT,
                ZoneId.of("Asia/Seoul"));
    }

    /**
     * 이 종류의 알림이 사용자에게 허용되는가? mandatory 종류는 무조건 true.
     */
    public boolean isAllowed(NotificationKind kind) {
        if (kind.mandatory()) {
            return true;
        }
        return allowedByKind.getOrDefault(kind, true);
    }

    /**
     * 이 종류의 알림이 들어왔을 때 어느 채널을 우선 시도할지. 비어 있으면 기본 정책.
     */
    public Set<ChannelType> preferredChannelsFor(NotificationKind kind) {
        return preferredChannels.getOrDefault(kind, Set.of());
    }

    public QuietHours quietHours() {
        return quietHours;
    }

    public ZoneId timezone() {
        return timezone;
    }

    public RecipientId recipientId() {
        return recipientId;
    }

    /**
     * 새 선호도로 갱신된 사본. 도메인 객체 자체는 불변.
     */
    public UserPreference withChannelOptOut(NotificationKind kind, boolean allowed) {
        if (kind.mandatory()) {
            throw new IllegalArgumentException(
                    "mandatory notification kind cannot be opted-out: " + kind);
        }
        EnumMap<NotificationKind, Boolean> copy = new EnumMap<>(allowedByKind);
        copy.put(kind, allowed);
        return new UserPreference(
                recipientId, copy, preferredChannels, quietHours, timezone);
    }

    public UserPreference withPreferredChannels(
            NotificationKind kind, Set<ChannelType> channels) {
        EnumMap<NotificationKind, Set<ChannelType>> copy = new EnumMap<>(preferredChannels);
        copy.put(kind, EnumSet.copyOf(channels));
        return new UserPreference(recipientId, allowedByKind, copy, quietHours, timezone);
    }

    public UserPreference withQuietHours(QuietHours quietHours) {
        return new UserPreference(
                recipientId, allowedByKind, preferredChannels, quietHours, timezone);
    }

    public UserPreference withTimezone(ZoneId timezone) {
        return new UserPreference(
                recipientId, allowedByKind, preferredChannels, quietHours, timezone);
    }
}
