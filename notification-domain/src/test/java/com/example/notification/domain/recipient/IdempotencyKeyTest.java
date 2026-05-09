package com.example.notification.domain.recipient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.notification.domain.shared.IdempotencyKey;
import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

    @Test
    void blank_rejected() {
        assertThatThrownBy(() -> new IdempotencyKey("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void too_long_rejected() {
        assertThatThrownBy(() -> new IdempotencyKey("x".repeat(200)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equals_by_value() {
        assertThat(new IdempotencyKey("abc")).isEqualTo(new IdempotencyKey("abc"));
        assertThat(new IdempotencyKey("abc")).isNotEqualTo(new IdempotencyKey("def"));
    }

    @Test
    void trims_input() {
        assertThat(new IdempotencyKey("  abc  ").value()).isEqualTo("abc");
    }
}
