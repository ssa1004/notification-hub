package com.example.notification.domain.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ChannelTest {

    @Test
    void valid_email() {
        Channel c = new Channel(ChannelType.EMAIL, "user@example.com");
        assertThat(c.address()).isEqualTo("user@example.com");
    }

    @Test
    void invalid_email_rejected() {
        assertThatThrownBy(() -> new Channel(ChannelType.EMAIL, "not-an-email"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sms_must_be_E164() {
        assertThatThrownBy(() -> new Channel(ChannelType.SMS, "010-1234-5678"))
                .isInstanceOf(IllegalArgumentException.class);
        Channel c = new Channel(ChannelType.SMS, "+821012345678");
        assertThat(c.type()).isEqualTo(ChannelType.SMS);
    }

    @Test
    void push_token_length_validated() {
        String token = "a".repeat(160);
        Channel c = new Channel(ChannelType.PUSH, token);
        assertThat(c.address()).hasSize(160);
        assertThatThrownBy(() -> new Channel(ChannelType.PUSH, "short"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toString_masks_address() {
        Channel c = new Channel(ChannelType.EMAIL, "user@example.com");
        assertThat(c.toString()).doesNotContain("user@example.com");
        assertThat(c.toString()).contains("***");
    }

    @Test
    void kakao_alimtalk_disallowed_at_night() {
        assertThat(ChannelType.KAKAO_ALIMTALK.allowedAtNight()).isFalse();
        assertThat(ChannelType.PUSH.allowedAtNight()).isTrue();
    }
}
