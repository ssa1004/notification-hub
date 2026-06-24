package com.example.notification.adapter.out.memory

import com.example.notification.application.port.out.RateLimiter
import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.recipient.RecipientId
import com.example.notification.domain.shared.RateLimitDecision
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * recipient × channel fixed-window token bucket 의 인메모리 구현 — zero-infra 부팅용.
 *
 * Redis INCR + PEXPIRE Lua 대신 윈도우 카운터([ConcurrentHashMap])로 같은 fixed-window 동작을
 * 재현한다. [tryConsumeAll] 은 RedisRateLimiter 의 Lua 배치와 동일하게 "모든 채널이 가능할 때만
 * 일괄 차감, 하나라도 막히면 전부 거절(토큰 유지)"을 보장한다 — coarse lock 으로 원자성 확보
 * (단일 인스턴스·데모 규모라 충분). 운영/분산에는 RedisRateLimiter(`notification.redis.enabled=true`).
 */
@Component
@ConditionalOnProperty(name = ["notification.redis.enabled"], havingValue = "false")
class InMemoryRateLimiter : RateLimiter {

    @Value("\${ratelimit.window-ms:60000}") private var windowMs: Long = 60_000
    @Value("\${ratelimit.push-per-window:30}") private var pushLimit: Long = 30
    @Value("\${ratelimit.email-per-window:30}") private var emailLimit: Long = 30
    @Value("\${ratelimit.sms-per-window:5}") private var smsLimit: Long = 5
    @Value("\${ratelimit.kakao-per-window:5}") private var kakaoLimit: Long = 5

    private class Window(var endMs: Long, var count: Long)

    private val windows = ConcurrentHashMap<String, Window>()
    private val lock = Any()

    override fun tryConsume(recipientId: RecipientId, channel: ChannelType): RateLimitDecision =
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val w = roll(key(channel, recipientId), now)
            w.count += 1
            val limit = limitFor(channel)
            if (w.count > limit) {
                RateLimitDecision.deny(w.endMs - now)
            } else {
                RateLimitDecision.allow(maxOf(0L, limit - w.count))
            }
        }

    override fun tryConsumeAll(
        recipientId: RecipientId,
        demand: Map<ChannelType, Int>,
    ): Map<ChannelType, RateLimitDecision> {
        if (demand.isEmpty()) return emptyMap()
        for ((k, v) in demand) require(v > 0) { "demand must be positive for $k: got $v" }
        return synchronized(lock) {
            val now = System.currentTimeMillis()
            // 결정성을 위해 enum 정의 순으로 정렬.
            val ordered = ChannelType.values().filter { demand.containsKey(it) }
            val ws = ordered.associateWith { roll(key(it, recipientId), now) }
            val result = EnumMap<ChannelType, RateLimitDecision>(ChannelType::class.java)

            val denied = ordered.any { ws.getValue(it).count + demand.getValue(it) > limitFor(it) }
            if (denied) {
                // 어느 하나라도 막히면 전부 거절 — 토큰은 그대로 둔다(부분 소진 leak 방지).
                for (t in ordered) {
                    val ttl = ws.getValue(t).endMs - now
                    result[t] = RateLimitDecision.deny(if (ttl >= 0) ttl else windowMs)
                }
            } else {
                for (t in ordered) {
                    val w = ws.getValue(t)
                    w.count += demand.getValue(t)
                    result[t] = RateLimitDecision.allow(maxOf(0L, limitFor(t) - w.count))
                }
            }
            result
        }
    }

    /** 만료된 윈도우는 새로 시작. (fixed-window — 윈도우 경계에서 카운트 리셋) */
    private fun roll(k: String, now: Long): Window {
        val w = windows.getOrPut(k) { Window(now + windowMs, 0) }
        if (now >= w.endMs) {
            w.endMs = now + windowMs
            w.count = 0
        }
        return w
    }

    private fun key(channel: ChannelType, recipientId: RecipientId): String =
        channel.name + ":" + recipientId.value

    private fun limitFor(channel: ChannelType): Long = when (channel) {
        ChannelType.PUSH -> pushLimit
        ChannelType.EMAIL -> emailLimit
        ChannelType.SMS -> smsLimit
        ChannelType.KAKAO_ALIMTALK -> kakaoLimit
    }
}
