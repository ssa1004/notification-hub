package com.example.notification.application.service

import com.example.notification.application.dto.DeliveryHistoryPage
import com.example.notification.application.port.`in`.ListMyDeliveriesUseCase
import com.example.notification.application.port.out.NotificationRepository
import com.example.notification.domain.recipient.RecipientId
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMyDeliveriesService(
    private val repository: NotificationRepository,
) : ListMyDeliveriesUseCase {

    @Transactional(readOnly = true)
    override fun list(recipientId: String, cursor: UUID?, limit: Int): DeliveryHistoryPage {
        val safeLimit = if (limit <= 0) DEFAULT_LIMIT else minOf(limit, MAX_LIMIT)
        return repository.findHistory(RecipientId(recipientId), cursor, safeLimit)
    }

    companion object {
        private const val MAX_LIMIT = 100
        private const val DEFAULT_LIMIT = 20
    }
}
