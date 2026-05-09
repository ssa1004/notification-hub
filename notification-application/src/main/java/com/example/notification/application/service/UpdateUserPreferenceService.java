package com.example.notification.application.service;

import com.example.notification.application.port.in.UpdateUserPreferenceUseCase;
import com.example.notification.application.port.out.UserPreferenceRepository;
import com.example.notification.domain.preference.UserPreference;
import com.example.notification.domain.recipient.RecipientId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateUserPreferenceService implements UpdateUserPreferenceUseCase {

    private final UserPreferenceRepository repository;

    @Override
    @Transactional
    public UserPreference update(UpdateCommand command) {
        RecipientId rid = new RecipientId(command.recipientId());
        UserPreference current = repository
                .findByRecipientId(rid)
                .orElseGet(() -> UserPreference.defaults(rid));

        UserPreference updated = current;
        if (command.kind() != null && command.allowed() != null) {
            updated = updated.withChannelOptOut(command.kind(), command.allowed());
        }
        if (command.kind() != null && command.preferredChannels() != null) {
            updated = updated.withPreferredChannels(command.kind(), command.preferredChannels());
        }
        if (command.disableQuietHours()) {
            updated = updated.withQuietHours(null);
        } else if (command.quietHours() != null) {
            updated = updated.withQuietHours(command.quietHours());
        }
        if (command.timezone() != null) {
            updated = updated.withTimezone(command.timezone());
        }
        return repository.save(updated);
    }
}
