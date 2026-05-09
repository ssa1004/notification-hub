package com.example.notification.domain.preference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.notification.NotificationKind;
import com.example.notification.domain.recipient.RecipientId;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserPreferenceTest {

    private final RecipientId user = new RecipientId("u-1");

    @Test
    void defaults_allow_everything() {
        UserPreference p = UserPreference.defaults(user);
        for (NotificationKind k : NotificationKind.values()) {
            assertThat(p.isAllowed(k)).isTrue();
        }
    }

    @Test
    void marketing_can_be_opted_out() {
        UserPreference p = UserPreference.defaults(user)
                .withChannelOptOut(NotificationKind.MARKETING, false);
        assertThat(p.isAllowed(NotificationKind.MARKETING)).isFalse();
        assertThat(p.isAllowed(NotificationKind.TRANSACTIONAL)).isTrue();
    }

    @Test
    void mandatory_kind_cannot_be_opted_out() {
        UserPreference p = UserPreference.defaults(user);
        assertThatThrownBy(
                        () -> p.withChannelOptOut(NotificationKind.SECURITY, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preferred_channels_replaceable() {
        UserPreference p = UserPreference.defaults(user)
                .withPreferredChannels(
                        NotificationKind.MARKETING, Set.of(ChannelType.EMAIL));
        assertThat(p.preferredChannelsFor(NotificationKind.MARKETING))
                .containsExactly(ChannelType.EMAIL);
    }
}
