package com.example.notification.application.port.out

import com.example.notification.domain.delivery.DeliveryAttempt
import com.example.notification.domain.delivery.DeliveryStatus
import java.util.Optional
import java.util.UUID

/** DeliveryAttempt persistence port. */
interface DeliveryAttemptRepository {

    fun save(attempt: DeliveryAttempt): DeliveryAttempt

    fun saveAll(attempts: List<DeliveryAttempt>): List<DeliveryAttempt>

    fun findById(id: UUID): Optional<DeliveryAttempt>

    fun findByNotificationId(notificationId: UUID): List<DeliveryAttempt>

    /**
     * DLQ 운영용 cursor 페이지네이션. 정해진 status (보통 EXHAUSTED) 의 attempt 중 cursor 보다
     * id 가 큰 것을 createdAt 오름차순으로 N개. cursor 가 null 이면 처음부터.
     */
    fun findByStatusAfter(status: DeliveryStatus, cursor: UUID?, limit: Int): List<DeliveryAttempt>
}
