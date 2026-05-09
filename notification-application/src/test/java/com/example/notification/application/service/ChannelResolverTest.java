package com.example.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.notification.domain.channel.Channel;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.notification.NotificationKind;
import com.example.notification.domain.preference.QuietHours;
import com.example.notification.domain.preference.UserPreference;
import com.example.notification.domain.recipient.Recipient;
import com.example.notification.domain.recipient.RecipientId;
import com.example.notification.domain.shared.Locale;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChannelResolverTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ChannelResolver resolver = new ChannelResolver();

    @Test
    void marketing_in_quiet_hours_returns_empty() {
        Recipient r = recipient(ChannelType.PUSH, ChannelType.EMAIL);
        UserPreference p = UserPreference.defaults(r.id());
        Instant atMidnight = at(0, 0);
        List<Channel> out = resolver.resolve(r, p, NotificationKind.MARKETING, atMidnight);
        assertThat(out).isEmpty();
    }

    @Test
    void security_bypasses_quiet_hours() {
        Recipient r = recipient(ChannelType.PUSH);
        UserPreference p = UserPreference.defaults(r.id());
        Instant atMidnight = at(0, 0);
        List<Channel> out = resolver.resolve(r, p, NotificationKind.SECURITY, atMidnight);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).type()).isEqualTo(ChannelType.PUSH);
    }

    @Test
    void opt_out_marketing_returns_empty_even_at_daytime() {
        Recipient r = recipient(ChannelType.PUSH);
        UserPreference p = UserPreference.defaults(r.id())
                .withChannelOptOut(NotificationKind.MARKETING, false);
        List<Channel> out = resolver.resolve(r, p, NotificationKind.MARKETING, at(12, 0));
        assertThat(out).isEmpty();
    }

    @Test
    void preferred_channels_filter_applied() {
        Recipient r = recipient(ChannelType.PUSH, ChannelType.EMAIL, ChannelType.SMS);
        UserPreference p = UserPreference.defaults(r.id())
                .withPreferredChannels(NotificationKind.MARKETING, Set.of(ChannelType.EMAIL));
        List<Channel> out = resolver.resolve(r, p, NotificationKind.MARKETING, at(12, 0));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).type()).isEqualTo(ChannelType.EMAIL);
    }

    @Test
    void transactional_at_quiet_hours_is_blocked_in_default_setting() {
        Recipient r = recipient(ChannelType.PUSH);
        UserPreference p = UserPreference.defaults(r.id());
        // TRANSACTIONAL is mandatory but respects quiet hours per kind config.
        List<Channel> out = resolver.resolve(r, p, NotificationKind.TRANSACTIONAL, at(0, 0));
        assertThat(out).isEmpty();
    }

    @Test
    void daytime_marketing_includes_all_eligible_types_in_canonical_order() {
        Recipient r = recipient(ChannelType.SMS, ChannelType.PUSH, ChannelType.EMAIL);
        UserPreference p = UserPreference.defaults(r.id());
        List<Channel> out = resolver.resolve(r, p, NotificationKind.MARKETING, at(12, 0));
        assertThat(out)
                .extracting(Channel::type)
                .containsExactly(ChannelType.PUSH, ChannelType.EMAIL, ChannelType.SMS);
    }

    @Test
    void kakao_alimtalk_blocked_at_night_for_security_too() {
        // SECURITY bypasses DND, but vendor policy still forbids 알림톡 야간 발송 → 채널만 제외.
        Recipient r = new Recipient(
                new RecipientId("u-1"),
                List.of(
                        new Channel(ChannelType.PUSH, "p".repeat(160)),
                        new Channel(ChannelType.KAKAO_ALIMTALK, "+821012345678")),
                Locale.KO_KR,
                KST);
        UserPreference p = UserPreference.defaults(r.id());
        List<Channel> out = resolver.resolve(r, p, NotificationKind.SECURITY, at(0, 0));
        assertThat(out).extracting(Channel::type).containsExactly(ChannelType.PUSH);
    }

    private Recipient recipient(ChannelType... types) {
        java.util.List<Channel> chs = new java.util.ArrayList<>();
        for (ChannelType t : types) {
            chs.add(switch (t) {
                case PUSH -> new Channel(ChannelType.PUSH, "p".repeat(160));
                case EMAIL -> new Channel(ChannelType.EMAIL, "user@example.com");
                case SMS -> new Channel(ChannelType.SMS, "+821012345678");
                case KAKAO_ALIMTALK -> new Channel(ChannelType.KAKAO_ALIMTALK, "+821012345678");
            });
        }
        return new Recipient(new RecipientId("u-1"), chs, Locale.KO_KR, KST);
    }

    private Instant at(int hour, int minute) {
        return ZonedDateTime.of(LocalDate.of(2026, 5, 9), LocalTime.of(hour, minute), KST)
                .toInstant();
    }
}
