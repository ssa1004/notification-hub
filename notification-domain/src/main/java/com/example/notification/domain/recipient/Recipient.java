package com.example.notification.domain.recipient;

import com.example.notification.domain.channel.Channel;
import com.example.notification.domain.shared.Locale;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * 알림 수신 대상. RecipientId + 등록된 모든 raw 채널 + locale + timezone 을 묶어 표현합니다.
 *
 * <p>여기서의 channels 는 사용자가 가진 *주소록* 입니다. 실제로 어느 채널이 활성화되었는지는
 * {@code UserPreference} 가 결정합니다 (opt-out 처리).
 */
public final class Recipient {

    private final RecipientId id;
    private final List<Channel> channels;
    private final Locale locale;
    private final ZoneId timezone;

    public Recipient(RecipientId id, List<Channel> channels, Locale locale, ZoneId timezone) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(channels, "channels must not be null");
        this.channels = List.copyOf(channels);
        this.locale = Objects.requireNonNullElse(locale, Locale.KO_KR);
        this.timezone = Objects.requireNonNullElse(timezone, ZoneId.of("Asia/Seoul"));
    }

    public RecipientId id() {
        return id;
    }

    public List<Channel> channels() {
        return channels;
    }

    public Locale locale() {
        return locale;
    }

    public ZoneId timezone() {
        return timezone;
    }
}
