package com.example.notification.application.port.out

import com.example.notification.domain.device.DeviceToken
import com.example.notification.domain.recipient.RecipientId
import java.util.Optional
import java.util.UUID

/** 모바일 push 채널의 device token CRUD. */
interface DeviceTokenRepository {

    fun save(token: DeviceToken): DeviceToken

    fun findById(id: UUID): Optional<DeviceToken>

    /** 사용자별 활성 토큰 (disabledAt is null). */
    fun findActiveByRecipientId(recipientId: RecipientId): List<DeviceToken>

    /** raw token 으로 조회 — 같은 토큰 중복 등록 방지 + vendor 영구 실패 시 비활성화. */
    fun findByToken(token: String): Optional<DeviceToken>

    /**
     * raw token 의 device 를 비활성화. 이미 비활성이면 no-op. vendor 가 영구 실패
     * (NOT_REGISTERED 등) 응답하면 호출.
     */
    fun deactivateByToken(token: String)
}
