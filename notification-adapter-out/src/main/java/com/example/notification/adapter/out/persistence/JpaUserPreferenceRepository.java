package com.example.notification.adapter.out.persistence;

import com.example.notification.adapter.out.persistence.mapper.UserPreferenceMapper;
import com.example.notification.adapter.out.persistence.repository.UserPreferenceJpaRepository;
import com.example.notification.application.port.out.UserPreferenceRepository;
import com.example.notification.domain.preference.UserPreference;
import com.example.notification.domain.recipient.RecipientId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaUserPreferenceRepository implements UserPreferenceRepository {

    private final UserPreferenceJpaRepository jpa;

    @Override
    public Optional<UserPreference> findByRecipientId(RecipientId recipientId) {
        return jpa.findById(recipientId.value()).map(UserPreferenceMapper::toDomain);
    }

    @Override
    public UserPreference save(UserPreference preference) {
        return UserPreferenceMapper.toDomain(jpa.save(UserPreferenceMapper.toEntity(preference)));
    }
}
