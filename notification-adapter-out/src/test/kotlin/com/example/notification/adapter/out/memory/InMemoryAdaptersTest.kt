package com.example.notification.adapter.out.memory

import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.recipient.RecipientId
import com.example.notification.domain.shared.IdempotencyKey
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * zero-infra 인메모리 어댑터(멱등성·rate limit)의 동작을 Redis 없이 단위로 고정한다.
 * Redis 어댑터와 같은 의미(같은 키 차단, fixed-window 한도, 묶음 all-or-nothing)를 보장하는지 검증.
 */
class InMemoryAdaptersTest {

    @Test
    fun `멱등성 - 같은 키 두 번째는 거절되고 release 후 재획득된다`() {
        val store = InMemoryIdempotencyStore()
        val key = IdempotencyKey("k1")
        assertThat(store.tryAcquire(key, Duration.ofMinutes(5))).isTrue()
        assertThat(store.tryAcquire(key, Duration.ofMinutes(5))).isFalse()
        store.release(key)
        assertThat(store.tryAcquire(key, Duration.ofMinutes(5))).isTrue()
    }

    @Test
    fun `멱등성 - 만료된 점유는 재획득 가능하다`() {
        val store = InMemoryIdempotencyStore()
        val key = IdempotencyKey("k2")
        assertThat(store.tryAcquire(key, Duration.ofMillis(1))).isTrue()
        Thread.sleep(10)
        assertThat(store.tryAcquire(key, Duration.ofMinutes(5))).isTrue()
    }

    @Test
    fun `rate limit - PUSH 는 한도(30)까지 허용 후 31번째 거절된다`() {
        val rl = InMemoryRateLimiter()
        val r = RecipientId("u1")
        repeat(30) { assertThat(rl.tryConsume(r, ChannelType.PUSH).allowed).isTrue() }
        assertThat(rl.tryConsume(r, ChannelType.PUSH).allowed).isFalse()
    }

    @Test
    fun `rate limit - tryConsumeAll 은 하나라도 막히면 전부 거절하고 다른 채널 토큰은 유지한다`() {
        val rl = InMemoryRateLimiter()
        val r = RecipientId("u2")
        repeat(5) { assertThat(rl.tryConsume(r, ChannelType.SMS).allowed).isTrue() } // SMS 한도 5 소진
        val res = rl.tryConsumeAll(r, mapOf(ChannelType.PUSH to 1, ChannelType.SMS to 1))
        assertThat(res.getValue(ChannelType.PUSH).allowed).isFalse()
        assertThat(res.getValue(ChannelType.SMS).allowed).isFalse()
        // PUSH 토큰은 차감되지 않아(부분 소진 leak 방지) 단독으로는 아직 허용된다.
        assertThat(rl.tryConsume(r, ChannelType.PUSH).allowed).isTrue()
    }

    @Test
    fun `admin rate limit - 한도(60)까지 허용 후 61번째 거절된다`() {
        val arl = InMemoryAdminRateLimiter()
        repeat(60) { assertThat(arl.tryConsume("list", "1.2.3.4").allowed).isTrue() }
        assertThat(arl.tryConsume("list", "1.2.3.4").allowed).isFalse()
    }
}
