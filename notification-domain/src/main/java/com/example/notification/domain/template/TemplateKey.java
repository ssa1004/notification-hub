package com.example.notification.domain.template;

import java.util.Objects;

/**
 * 템플릿 식별자. 운영자가 등록한 템플릿을 use case 가 참조할 때 쓰는 안정 키.
 *
 * <p>예: {@code order.shipped.v1}, {@code auth.otp.v2}.
 *
 * <p>관례: {@code <도메인>.<이벤트>.v<버전>}. 버전이 바뀌면 새 row 로 등록.
 */
public final class TemplateKey {

    private final String value;

    public TemplateKey(String value) {
        Objects.requireNonNull(value, "templateKey must not be null");
        String trimmed = value.trim();
        if (!trimmed.matches("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")) {
            throw new IllegalArgumentException(
                    "templateKey must match `^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$`: " + value);
        }
        this.value = trimmed;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TemplateKey other)) return false;
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
