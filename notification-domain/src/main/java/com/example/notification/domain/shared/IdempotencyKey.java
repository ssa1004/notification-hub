package com.example.notification.domain.shared;

import java.util.Objects;

/**
 * 같은 요청이 두 번 도착해도 한 번만 처리되게 만드는 멱등성 키.
 *
 * <p>호출자가 임의 문자열을 만들어 헤더 (`Idempotency-Key`) 로 전달합니다. 서버는 이 키를
 * Redis SETNX (키가 없을 때만 set, 있으면 실패하는 원자 연산) 로 점유한 뒤 use case 진입을
 * 허용합니다. TTL 24시간 — 같은 키 재사용 금지 기간.
 */
public final class IdempotencyKey {

    private static final int MAX_LEN = 128;

    private final String value;

    public IdempotencyKey(String value) {
        Objects.requireNonNull(value, "idempotencyKey must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (trimmed.length() > MAX_LEN) {
            throw new IllegalArgumentException(
                    "idempotencyKey too long (max " + MAX_LEN + ")");
        }
        this.value = trimmed;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyKey other)) return false;
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
