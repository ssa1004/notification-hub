package com.example.notification.adapter.out.persistence.mapper

import com.example.notification.adapter.out.persistence.entity.RecipientEntity
import com.example.notification.domain.channel.Channel
import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.recipient.Recipient
import com.example.notification.domain.recipient.RecipientId
import com.example.notification.domain.shared.Locale
import com.fasterxml.jackson.core.type.TypeReference
import java.time.ZoneId

object RecipientMapper {

    @JvmStatic
    fun toDomain(e: RecipientEntity): Recipient {
        val channelsRaw: List<Map<String, String>> =
            JsonMapper.readValue(e.channelsJson, object : TypeReference<List<Map<String, String>>>() {})
        val channels = channelsRaw.map { ch ->
            Channel(ChannelType.valueOf(ch["type"]!!), ch["address"]!!)
        }
        return Recipient(
            RecipientId(e.id),
            channels,
            Locale(e.locale),
            ZoneId.of(e.timezone),
        )
    }

    @JvmStatic
    fun toEntity(r: Recipient): RecipientEntity = RecipientEntity().apply {
        id = r.id.value
        val chs = r.channels.map { c -> mapOf("type" to c.type.name, "address" to c.address) }
        channelsJson = try {
            JsonMapper.objectMapper().writeValueAsString(chs)
        } catch (ex: Exception) {
            throw IllegalStateException("channels json serialize failed", ex)
        }
        locale = r.locale.tag
        timezone = r.timezone.id
    }
}
