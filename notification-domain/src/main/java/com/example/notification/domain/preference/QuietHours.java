package com.example.notification.domain.preference;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * 방해금지 시간 (Do Not Disturb). 사용자 timezone 기준 [start, end) 구간에 있는 알림은
 * 즉시 발송하지 않고 보류 (또는 즉시 차단) 합니다.
 *
 * <p>{@link #DEFAULT} 는 22:00~08:00 — 일반적인 한국 야간 시간대.
 *
 * <p>start > end 인 경우 (예: 22:00~08:00) 는 자정을 넘는 윈도우로 해석합니다.
 */
public final class QuietHours {

    public static final QuietHours DEFAULT = new QuietHours(LocalTime.of(22, 0), LocalTime.of(8, 0));
    public static final QuietHours DISABLED = null;

    private final LocalTime start;
    private final LocalTime end;

    public QuietHours(LocalTime start, LocalTime end) {
        this.start = Objects.requireNonNull(start, "start must not be null");
        this.end = Objects.requireNonNull(end, "end must not be null");
        if (start.equals(end)) {
            throw new IllegalArgumentException("start and end must differ");
        }
    }

    public LocalTime start() {
        return start;
    }

    public LocalTime end() {
        return end;
    }

    /**
     * 주어진 시각이 방해금지 윈도우 안인가?
     *
     * @param at 검사할 절대 시각
     * @param zone 사용자 timezone (한국이면 Asia/Seoul)
     */
    public boolean contains(java.time.Instant at, ZoneId zone) {
        ZonedDateTime zoned = at.atZone(zone);
        LocalTime t = zoned.toLocalTime();
        if (start.isBefore(end)) {
            // 같은 날짜 안 (예: 12:00~14:00)
            return !t.isBefore(start) && t.isBefore(end);
        }
        // 자정 넘김 (예: 22:00~08:00)
        return !t.isBefore(start) || t.isBefore(end);
    }
}
