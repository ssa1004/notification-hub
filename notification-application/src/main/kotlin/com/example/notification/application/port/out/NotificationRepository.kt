package com.example.notification.application.port.out

import com.example.notification.application.dto.DeliveryHistoryPage
import com.example.notification.domain.notification.Notification
import com.example.notification.domain.recipient.RecipientId
import java.util.Optional
import java.util.UUID

/** Notification aggregate persistence port. */
interface NotificationRepository {

    fun save(notification: Notification): Notification

    fun findById(id: UUID): Optional<Notification>

    /**
     * 사용자의 알림 이력. cursor 페이지네이션 — `cursor` 는 직전 페이지의 마지막 createdAt 의
     * UUID 직렬화 값. 없으면 가장 최근부터.
     *
     * @param recipientId 사용자
     * @param cursor 직전 페이지 마지막 row 의 id (없으면 null = 처음 페이지)
     * @param limit 페이지 크기 (서버에서 max 100 제한)
     */
    fun findHistory(recipientId: RecipientId, cursor: UUID?, limit: Int): DeliveryHistoryPage
}
