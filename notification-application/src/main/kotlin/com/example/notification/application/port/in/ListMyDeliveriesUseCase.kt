package com.example.notification.application.port.`in`

import com.example.notification.application.dto.DeliveryHistoryPage
import java.util.UUID

/** 사용자가 자기 알림 이력 조회. cursor 페이지네이션 (limit max 100). */
interface ListMyDeliveriesUseCase {

    fun list(recipientId: String, cursor: UUID?, limit: Int): DeliveryHistoryPage
}
