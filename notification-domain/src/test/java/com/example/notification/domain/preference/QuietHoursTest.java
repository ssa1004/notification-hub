package com.example.notification.domain.preference;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class QuietHoursTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Test
    void default_window_22_to_8_includes_midnight() {
        Instant atMidnight = ZonedDateTime.of(LocalDate.of(2026, 5, 9), LocalTime.of(0, 0), KST)
                .toInstant();
        assertThat(QuietHours.DEFAULT.contains(atMidnight, KST)).isTrue();
    }

    @Test
    void default_window_excludes_noon() {
        Instant atNoon = ZonedDateTime.of(LocalDate.of(2026, 5, 9), LocalTime.NOON, KST)
                .toInstant();
        assertThat(QuietHours.DEFAULT.contains(atNoon, KST)).isFalse();
    }

    @Test
    void start_inclusive_end_exclusive() {
        Instant at22 = ZonedDateTime.of(LocalDate.of(2026, 5, 9), LocalTime.of(22, 0), KST)
                .toInstant();
        Instant at8 = ZonedDateTime.of(LocalDate.of(2026, 5, 9), LocalTime.of(8, 0), KST)
                .toInstant();
        assertThat(QuietHours.DEFAULT.contains(at22, KST)).isTrue();
        assertThat(QuietHours.DEFAULT.contains(at8, KST)).isFalse();
    }

    @Test
    void same_day_window_does_not_wrap() {
        QuietHours window = new QuietHours(LocalTime.of(12, 0), LocalTime.of(14, 0));
        Instant at13 = ZonedDateTime.of(LocalDate.of(2026, 5, 9), LocalTime.of(13, 0), KST)
                .toInstant();
        Instant at15 = ZonedDateTime.of(LocalDate.of(2026, 5, 9), LocalTime.of(15, 0), KST)
                .toInstant();
        assertThat(window.contains(at13, KST)).isTrue();
        assertThat(window.contains(at15, KST)).isFalse();
    }
}
