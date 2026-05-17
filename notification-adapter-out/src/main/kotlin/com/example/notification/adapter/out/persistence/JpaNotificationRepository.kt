package com.example.notification.adapter.out.persistence

import com.example.notification.adapter.out.persistence.mapper.NotificationMapper
import com.example.notification.adapter.out.persistence.repository.NotificationJpaRepository
import com.example.notification.application.dto.DeliveryHistoryPage
import com.example.notification.application.port.out.NotificationRepository
import com.example.notification.domain.notification.Notification
import com.example.notification.domain.recipient.RecipientId
import java.util.Optional
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

@Repository
class JpaNotificationRepository(
    private val jpa: NotificationJpaRepository,
) : NotificationRepository {

    override fun save(notification: Notification): Notification {
        val saved = jpa.save(NotificationMapper.toEntity(notification))
        return NotificationMapper.toDomain(saved)
    }

    override fun findById(id: UUID): Optional<Notification> =
        jpa.findById(id).map(NotificationMapper::toDomain)

    override fun findHistory(recipientId: RecipientId, cursor: UUID?, limit: Int): DeliveryHistoryPage {
        val cursorTs = if (cursor != null) {
            jpa.findById(cursor).map { it.createdAt }.orElse(null)
        } else {
            null
        }
        // limit + 1 만 fetch 해서 다음 페이지 존재 여부 판단.
        // cursor 유/무를 두 쿼리로 분기 — repository 주석 참조 (PG NULL 파라미터 타입 추론 이슈).
        val pageReq = PageRequest.of(0, limit + 1)
        val rows =
            if (cursorTs == null) {
                jpa.findHistoryFirstPage(recipientId.value, pageReq)
            } else {
                jpa.findHistoryAfterCursor(recipientId.value, cursorTs, pageReq)
            }
        val hasMore = rows.size > limit
        val page = if (hasMore) rows.subList(0, limit) else rows

        val items = page.map { e ->
            DeliveryHistoryPage.Item(
                e.id,
                e.title,
                e.kind.name,
                e.status.name,
                e.createdAt,
            )
        }
        val nextCursor = if (hasMore) page.last().id else null
        return DeliveryHistoryPage(items, nextCursor)
    }
}
