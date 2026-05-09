package com.example.notification.domain.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.notification.domain.channel.Channel;
import com.example.notification.domain.channel.ChannelType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryAttemptTest {

    private static final Channel EMAIL =
            new Channel(ChannelType.EMAIL, "user@example.com");

    @Test
    void create_starts_PENDING_with_zero_retries() {
        DeliveryAttempt a = sample();
        assertThat(a.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(a.retryCount()).isZero();
        assertThat(a.nextAttemptAt()).isNotNull();
    }

    @Test
    void successful_lifecycle() {
        DeliveryAttempt a = sample();
        a.markDispatching();
        a.markSucceeded("vendor-msg-1");
        assertThat(a.status()).isEqualTo(DeliveryStatus.SUCCEEDED);
        assertThat(a.vendorMessageId()).isEqualTo("vendor-msg-1");
        assertThat(a.completedAt()).isNotNull();
        assertThat(a.isFinal()).isTrue();
    }

    @Test
    void fail_below_max_returns_to_PENDING_and_increments_retry() {
        DeliveryAttempt a = sample();
        a.markDispatching();
        a.markFailed("vendor 5xx");
        assertThat(a.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(a.retryCount()).isEqualTo(1);
        assertThat(a.failureReason()).contains("5xx");
    }

    @Test
    void fail_at_max_moves_to_EXHAUSTED() {
        DeliveryAttempt a = sample();
        for (int i = 0; i < DeliveryAttempt.MAX_RETRY; i++) {
            a.markDispatching();
            a.markFailed("vendor down");
        }
        assertThat(a.status()).isEqualTo(DeliveryStatus.EXHAUSTED);
        assertThat(a.isFinal()).isTrue();
    }

    @Test
    void cannot_succeed_without_dispatching() {
        DeliveryAttempt a = sample();
        assertThatThrownBy(() -> a.markSucceeded("x"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void backoff_grows_exponentially_and_is_capped() {
        assertThat(DeliveryAttempt.backoffFor(1).toMillis()).isEqualTo(1_000);
        assertThat(DeliveryAttempt.backoffFor(2).toMillis()).isEqualTo(2_000);
        assertThat(DeliveryAttempt.backoffFor(3).toMillis()).isEqualTo(4_000);
        assertThat(DeliveryAttempt.backoffFor(10).toMillis()).isEqualTo(60_000);
    }

    private DeliveryAttempt sample() {
        return DeliveryAttempt.create(UUID.randomUUID(), EMAIL, "title", "body");
    }
}
