package com.example.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.notification.application.port.in.UpdateUserPreferenceUseCase.UpdateCommand;
import com.example.notification.application.port.out.UserPreferenceRepository;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.notification.NotificationKind;
import com.example.notification.domain.preference.QuietHours;
import com.example.notification.domain.preference.UserPreference;
import com.example.notification.domain.recipient.RecipientId;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateUserPreferenceServiceTest {

    @Mock UserPreferenceRepository repository;
    UpdateUserPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new UpdateUserPreferenceService(repository);
    }

    @Test
    void marketing_opt_out_persisted() {
        when(repository.findByRecipientId(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserPreference saved = service.update(
                new UpdateCommand(
                        "u-1", NotificationKind.MARKETING, false, null, null, false, null));
        assertThat(saved.isAllowed(NotificationKind.MARKETING)).isFalse();
    }

    @Test
    void quiet_hours_can_be_disabled() {
        when(repository.findByRecipientId(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserPreference saved = service.update(
                new UpdateCommand("u-1", null, null, null, null, true, null));
        assertThat(saved.quietHours()).isNull();
    }

    @Test
    void quiet_hours_can_be_replaced() {
        when(repository.findByRecipientId(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuietHours newWindow = new QuietHours(LocalTime.of(23, 0), LocalTime.of(7, 0));
        UserPreference saved = service.update(
                new UpdateCommand(
                        "u-1", null, null, null, newWindow, false, null));
        assertThat(saved.quietHours()).isEqualTo(newWindow);
    }

    @Test
    void preferred_channels_replace_for_kind() {
        when(repository.findByRecipientId(any()))
                .thenReturn(Optional.of(UserPreference.defaults(new RecipientId("u-1"))));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserPreference saved = service.update(
                new UpdateCommand(
                        "u-1",
                        NotificationKind.SERVICE,
                        null,
                        Set.of(ChannelType.EMAIL),
                        null,
                        false,
                        null));
        assertThat(saved.preferredChannelsFor(NotificationKind.SERVICE))
                .containsExactly(ChannelType.EMAIL);
    }
}
