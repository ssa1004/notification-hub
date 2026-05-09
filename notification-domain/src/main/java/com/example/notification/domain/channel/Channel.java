package com.example.notification.domain.channel;

import java.util.Objects;

/**
 * 한 채널을 식별하는 VO. 사용자 1명이 같은 채널을 여러 개 가질 수도 있어
 * (예: 회사 이메일 + 개인 이메일) {@code address} 까지 묶여야 정확한 식별이 됩니다.
 *
 * <p>각 vendor 의 raw address 형식:
 * <ul>
 *   <li>{@code PUSH}: FCM device token (보통 152~163 chars)
 *   <li>{@code EMAIL}: RFC 5322 email
 *   <li>{@code SMS} / {@code KAKAO_ALIMTALK}: E.164 phone number (예: +821012345678)
 * </ul>
 *
 * <p>도메인은 형식 검증까지만 책임지고 실제 도달 가능 여부는 vendor 가 판단합니다.
 */
public final class Channel {

    private final ChannelType type;
    private final String address;

    public Channel(ChannelType type, String address) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(address, "address must not be null");
        String trimmed = address.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("address must not be blank");
        }
        validate(type, trimmed);
        this.address = trimmed;
    }

    private static void validate(ChannelType type, String address) {
        switch (type) {
            case EMAIL -> {
                if (!address.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                    throw new IllegalArgumentException("invalid email: " + address);
                }
            }
            case SMS, KAKAO_ALIMTALK -> {
                if (!address.matches("^\\+\\d{8,15}$")) {
                    throw new IllegalArgumentException(
                            "invalid phone (E.164 expected): " + address);
                }
            }
            case PUSH -> {
                if (address.length() < 32 || address.length() > 256) {
                    throw new IllegalArgumentException(
                            "invalid push token length: " + address.length());
                }
            }
        }
    }

    public ChannelType type() {
        return type;
    }

    public String address() {
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Channel other)) return false;
        return type == other.type && address.equals(other.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, address);
    }

    @Override
    public String toString() {
        return type + ":" + maskAddress();
    }

    /** 로그 / audit 에 raw address 가 그대로 찍히지 않도록 일부 마스킹. */
    private String maskAddress() {
        if (address.length() <= 4) return "****";
        return address.substring(0, 2) + "***" + address.substring(address.length() - 2);
    }
}
