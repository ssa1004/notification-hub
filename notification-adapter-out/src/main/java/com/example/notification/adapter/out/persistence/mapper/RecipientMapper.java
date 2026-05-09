package com.example.notification.adapter.out.persistence.mapper;

import com.example.notification.adapter.out.persistence.entity.RecipientEntity;
import com.example.notification.domain.channel.Channel;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.recipient.Recipient;
import com.example.notification.domain.recipient.RecipientId;
import com.example.notification.domain.shared.Locale;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RecipientMapper {

    private RecipientMapper() {}

    public static Recipient toDomain(RecipientEntity e) {
        List<Map<String, String>> channelsRaw =
                JsonMapper.readValue(e.getChannelsJson(), new TypeReference<>() {});
        List<Channel> channels = new ArrayList<>();
        for (Map<String, String> ch : channelsRaw) {
            channels.add(
                    new Channel(
                            ChannelType.valueOf(ch.get("type")),
                            ch.get("address")));
        }
        return new Recipient(
                new RecipientId(e.getId()),
                channels,
                new Locale(e.getLocale()),
                ZoneId.of(e.getTimezone()));
    }

    public static RecipientEntity toEntity(Recipient r) {
        RecipientEntity e = new RecipientEntity();
        e.setId(r.id().value());
        List<Map<String, String>> chs = new ArrayList<>();
        for (Channel c : r.channels()) {
            chs.add(Map.of("type", c.type().name(), "address", c.address()));
        }
        try {
            e.setChannelsJson(JsonMapper.objectMapper().writeValueAsString(chs));
        } catch (Exception ex) {
            throw new IllegalStateException("channels json serialize failed", ex);
        }
        e.setLocale(r.locale().tag());
        e.setTimezone(r.timezone().getId());
        return e;
    }
}
