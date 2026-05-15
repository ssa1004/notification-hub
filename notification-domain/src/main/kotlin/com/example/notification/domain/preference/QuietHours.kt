package com.example.notification.domain.preference

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * 방해금지 시간 (Do Not Disturb). 사용자 timezone 기준 [start, end) 구간에 있는 알림은
 * 즉시 발송하지 않고 보류 (또는 즉시 차단) 합니다.
 *
 * [DEFAULT] 는 22:00~08:00 — 일반적인 한국 야간 시간대.
 *
 * start > end 인 경우 (예: 22:00~08:00) 는 자정을 넘는 윈도우로 해석합니다.
 */
class QuietHours(start: LocalTime, end: LocalTime) {

    @get:JvmName("start")
    val start: LocalTime = start

    @get:JvmName("end")
    val end: LocalTime = end

    init {
        require(start != end) { "start and end must differ" }
    }

    /**
     * 주어진 시각이 방해금지 윈도우 안인가?
     *
     * @param at 검사할 절대 시각
     * @param zone 사용자 timezone (한국이면 Asia/Seoul)
     */
    fun contains(at: Instant, zone: ZoneId): Boolean {
        val t = at.atZone(zone).toLocalTime()
        return if (start.isBefore(end)) {
            // 같은 날짜 안 (예: 12:00~14:00)
            !t.isBefore(start) && t.isBefore(end)
        } else {
            // 자정 넘김 (예: 22:00~08:00)
            !t.isBefore(start) || t.isBefore(end)
        }
    }

    companion object {
        @JvmField
        val DEFAULT: QuietHours = QuietHours(LocalTime.of(22, 0), LocalTime.of(8, 0))

        /** DND 비활성을 의미하는 null 상수 — 기존 Java API 호환을 위해 유지. */
        @JvmField
        val DISABLED: QuietHours? = null
    }
}
