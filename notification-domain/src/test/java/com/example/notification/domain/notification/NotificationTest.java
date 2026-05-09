package com.example.notification.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.notification.domain.recipient.RecipientId;
import com.example.notification.domain.shared.IdempotencyKey;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationTest {

    @Test
    void accept_creates_ACCEPTED() {
        Notification n = sample();
        assertThat(n.status()).isEqualTo(NotificationStatus.ACCEPTED);
        assertThat(n.id()).isNotNull();
        assertThat(n.createdAt()).isNotNull();
    }

    @Test
    void title_and_body_validated() {
        assertThatThrownBy(
                        () ->
                                Notification.accept(
                                        new IdempotencyKey("k"),
                                        new RecipientId("u-1"),
                                        NotificationKind.MARKETING,
                                        "",
                                        "ok",
                                        Map.of(),
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void state_transition_ACCEPTED_to_FANNED_OUT_to_COMPLETED() {
        Notification n = sample();
        n.markFannedOut();
        assertThat(n.status()).isEqualTo(NotificationStatus.FANNED_OUT);
        n.markCompleted();
        assertThat(n.status()).isEqualTo(NotificationStatus.COMPLETED);
    }

    @Test
    void cannot_complete_from_ACCEPTED() {
        Notification n = sample();
        assertThatThrownBy(n::markCompleted)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannot_fanout_from_SUPPRESSED() {
        Notification n = sample();
        n.markSuppressed();
        assertThatThrownBy(n::markFannedOut)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void payload_is_immutable_view() {
        Notification n = sample();
        assertThatThrownBy(() -> n.payload().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private Notification sample() {
        return Notification.accept(
                new IdempotencyKey("idem-1"),
                new RecipientId("user-1"),
                NotificationKind.MARKETING,
                "title",
                "body",
                Map.of("name", "홍길동"),
                null);
    }
}
