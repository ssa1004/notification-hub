package com.example.notification.adapter.out.persistence.mapper;

import com.example.notification.adapter.out.persistence.entity.UserPreferenceEntity;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.notification.NotificationKind;
import com.example.notification.domain.preference.QuietHours;
import com.example.notification.domain.preference.UserPreference;
import com.example.notification.domain.recipient.RecipientId;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UserPreferenceMapper {

    private UserPreferenceMapper() {}

    public static UserPreferenceEntity toEntity(UserPreference p) {
        UserPreferenceEntity e = new UserPreferenceEntity();
        e.setRecipientId(p.recipientId().value());

        Map<String, Boolean> allowed = new LinkedHashMap<>();
        for (NotificationKind k : NotificationKind.values()) {
            allowed.put(k.name(), p.isAllowed(k));
        }
        e.setAllowedJson(JsonMapper.writeMap(allowed));

        Map<String, List<String>> preferred = new LinkedHashMap<>();
        for (NotificationKind k : NotificationKind.values()) {
            Set<ChannelType> chs = p.preferredChannelsFor(k);
            preferred.put(k.name(), chs.stream().map(Enum::name).toList());
        }
        e.setPreferredJson(JsonMapper.writeMap(preferred));

        if (p.quietHours() != null) {
            e.setQuietStart(p.quietHours().start().toString());
            e.setQuietEnd(p.quietHours().end().toString());
        } else {
            e.setQuietStart(null);
            e.setQuietEnd(null);
        }
        e.setTimezone(p.timezone().getId());
        return e;
    }

    public static UserPreference toDomain(UserPreferenceEntity e) {
        Map<String, Boolean> allowedRaw =
                JsonMapper.readValue(e.getAllowedJson(), new TypeReference<>() {});
        Map<NotificationKind, Boolean> allowed = new EnumMap<>(NotificationKind.class);
        allowedRaw.forEach((k, v) -> allowed.put(NotificationKind.valueOf(k), v));

        Map<String, List<String>> preferredRaw =
                JsonMapper.readValue(e.getPreferredJson(), new TypeReference<>() {});
        Map<NotificationKind, Set<ChannelType>> preferred = new EnumMap<>(NotificationKind.class);
        preferredRaw.forEach(
                (k, list) -> {
                    Set<ChannelType> set = EnumSet.noneOf(ChannelType.class);
                    for (String s : list) {
                        set.add(ChannelType.valueOf(s));
                    }
                    if (!set.isEmpty()) {
                        preferred.put(NotificationKind.valueOf(k), set);
                    }
                });

        QuietHours qh = null;
        if (e.getQuietStart() != null && e.getQuietEnd() != null) {
            qh = new QuietHours(LocalTime.parse(e.getQuietStart()), LocalTime.parse(e.getQuietEnd()));
        }
        return new UserPreference(
                new RecipientId(e.getRecipientId()),
                new HashMap<>(allowed),
                preferred,
                qh,
                ZoneId.of(e.getTimezone()));
    }
}
