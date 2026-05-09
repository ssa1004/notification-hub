package com.example.notification.domain.shared;

import java.util.Objects;

/**
 * 사용자 언어 / 지역. 템플릿 본문이 locale 별로 다르므로 템플릿 조회 키의 일부.
 *
 * <p>BCP-47 lowercase 형식 (예: `ko-kr`, `en-us`). 시스템 기본 fallback 은 `ko-kr`.
 */
public final class Locale {

    public static final Locale KO_KR = new Locale("ko-kr");
    public static final Locale EN_US = new Locale("en-us");

    private final String tag;

    public Locale(String tag) {
        Objects.requireNonNull(tag, "locale tag must not be null");
        String normalized = tag.trim().toLowerCase();
        if (!normalized.matches("^[a-z]{2}(-[a-z0-9]{2,8})?$")) {
            throw new IllegalArgumentException("invalid locale tag: " + tag);
        }
        this.tag = normalized;
    }

    public String tag() {
        return tag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Locale other)) return false;
        return tag.equals(other.tag);
    }

    @Override
    public int hashCode() {
        return tag.hashCode();
    }

    @Override
    public String toString() {
        return tag;
    }
}
