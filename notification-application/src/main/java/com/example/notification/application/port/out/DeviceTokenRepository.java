package com.example.notification.application.port.out;

import com.example.notification.domain.device.DeviceToken;
import com.example.notification.domain.recipient.RecipientId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 모바일 push 채널의 device token CRUD. */
public interface DeviceTokenRepository {

    DeviceToken save(DeviceToken token);

    Optional<DeviceToken> findById(UUID id);

    /** 사용자별 활성 토큰 (disabledAt is null). */
    List<DeviceToken> findActiveByRecipientId(RecipientId recipientId);

    /** raw token 으로 조회 — 같은 토큰 중복 등록 방지. */
    Optional<DeviceToken> findByToken(String token);
}
