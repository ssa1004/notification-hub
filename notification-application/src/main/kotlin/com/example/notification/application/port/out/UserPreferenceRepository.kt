package com.example.notification.application.port.out

import com.example.notification.domain.preference.UserPreference
import com.example.notification.domain.recipient.RecipientId
import java.util.Optional

/** 사용자별 선호도 조회/저장. */
interface UserPreferenceRepository {

    fun findByRecipientId(recipientId: RecipientId): Optional<UserPreference>

    fun save(preference: UserPreference): UserPreference
}
