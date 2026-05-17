package com.example.notification.adapter.out.persistence.mapper

import com.example.notification.adapter.out.persistence.entity.UserPreferenceEntity
import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.notification.NotificationKind
import com.example.notification.domain.preference.QuietHours
import com.example.notification.domain.preference.UserPreference
import com.example.notification.domain.recipient.RecipientId
import com.fasterxml.jackson.core.type.TypeReference
import java.time.LocalTime
import java.time.ZoneId
import java.util.EnumMap
import java.util.EnumSet
import java.util.LinkedHashMap

object UserPreferenceMapper {

    @JvmStatic
    fun toEntity(p: UserPreference): UserPreferenceEntity = UserPreferenceEntity().apply {
        recipientId = p.recipientId.value

        val allowed = LinkedHashMap<String, Boolean>()
        for (k in NotificationKind.values()) {
            allowed[k.name] = p.isAllowed(k)
        }
        allowedJson = JsonMapper.writeMap(allowed)

        val preferred = LinkedHashMap<String, List<String>>()
        for (k in NotificationKind.values()) {
            val chs = p.preferredChannelsFor(k)
            preferred[k.name] = chs.map { it.name }
        }
        preferredJson = JsonMapper.writeMap(preferred)

        if (p.quietHours != null) {
            quietStart = p.quietHours!!.start.toString()
            quietEnd = p.quietHours!!.end.toString()
        } else {
            quietStart = null
            quietEnd = null
        }
        timezone = p.timezone.id
    }

    @JvmStatic
    fun toDomain(e: UserPreferenceEntity): UserPreference {
        val allowedRaw: Map<String, Boolean> =
            JsonMapper.readValue(e.allowedJson, object : TypeReference<Map<String, Boolean>>() {})
        val allowed = EnumMap<NotificationKind, Boolean>(NotificationKind::class.java)
        allowedRaw.forEach { (k, v) -> allowed[NotificationKind.valueOf(k)] = v }

        val preferredRaw: Map<String, List<String>> =
            JsonMapper.readValue(e.preferredJson, object : TypeReference<Map<String, List<String>>>() {})
        val preferred = EnumMap<NotificationKind, Set<ChannelType>>(NotificationKind::class.java)
        preferredRaw.forEach { (k, list) ->
            val set = EnumSet.noneOf(ChannelType::class.java)
            list.forEach { s -> set.add(ChannelType.valueOf(s)) }
            if (set.isNotEmpty()) {
                preferred[NotificationKind.valueOf(k)] = set
            }
        }

        val qh: QuietHours? = if (e.quietStart != null && e.quietEnd != null) {
            QuietHours(LocalTime.parse(e.quietStart), LocalTime.parse(e.quietEnd))
        } else {
            null
        }
        return UserPreference(
            RecipientId(e.recipientId),
            HashMap(allowed),
            preferred,
            qh,
            ZoneId.of(e.timezone),
        )
    }
}
