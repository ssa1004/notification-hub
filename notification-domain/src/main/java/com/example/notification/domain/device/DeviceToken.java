package com.example.notification.domain.device;

import com.example.notification.domain.recipient.RecipientId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 모바일 push 채널의 device token (FCM / APNs). 한 사용자가 여러 디바이스를 가질 수 있습니다.
 *
 * <p>토큰은 vendor 가 발급한 raw string. 같은 디바이스라도 OS 가 토큰을 회전시키면 새 row
 * 로 등록되고 과거 토큰은 {@code disabledAt} 이 설정됩니다.
 *
 * <p>vendor 콜백에서 InvalidRegistration / NotRegistered 응답을 받으면 즉시
 * {@link #disable()} 처리하여 다음 발송에서 건너뜀.
 */
public final class DeviceToken {

    private final UUID id;
    private final RecipientId recipientId;
    private final Platform platform;
    private final String token;
    private final Instant registeredAt;
    private Instant disabledAt;

    public DeviceToken(
            UUID id,
            RecipientId recipientId,
            Platform platform,
            String token,
            Instant registeredAt,
            Instant disabledAt) {
        this.id = Objects.requireNonNull(id);
        this.recipientId = Objects.requireNonNull(recipientId);
        this.platform = Objects.requireNonNull(platform);
        Objects.requireNonNull(token, "token must not be null");
        if (token.length() < 32 || token.length() > 256) {
            throw new IllegalArgumentException("invalid device token length");
        }
        this.token = token;
        this.registeredAt = Objects.requireNonNull(registeredAt);
        this.disabledAt = disabledAt;
    }

    public static DeviceToken register(RecipientId recipientId, Platform platform, String token) {
        return new DeviceToken(
                UUID.randomUUID(), recipientId, platform, token, Instant.now(), null);
    }

    public void disable() {
        if (this.disabledAt == null) {
            this.disabledAt = Instant.now();
        }
    }

    public boolean isActive() {
        return disabledAt == null;
    }

    public UUID id() {
        return id;
    }

    public RecipientId recipientId() {
        return recipientId;
    }

    public Platform platform() {
        return platform;
    }

    public String token() {
        return token;
    }

    public Instant registeredAt() {
        return registeredAt;
    }

    public Instant disabledAt() {
        return disabledAt;
    }

    public enum Platform {
        ANDROID,
        IOS,
        WEB
    }
}
