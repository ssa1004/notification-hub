package com.example.notification.domain.recipient;

import java.util.Objects;

/**
 * 수신자 (사용자) 식별자. 외부 시스템 (auth / member service) 의 user id 와 동일.
 *
 * <p>UUID 또는 임의 문자열을 허용 — 다양한 외부 시스템과의 호환을 위해 형식을 강제하지 않음.
 */
public final class RecipientId {

    private final String value;

    public RecipientId(String value) {
        Objects.requireNonNull(value, "recipientId must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("recipientId must not be blank");
        }
        if (trimmed.length() > 128) {
            throw new IllegalArgumentException("recipientId too long");
        }
        this.value = trimmed;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecipientId other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
