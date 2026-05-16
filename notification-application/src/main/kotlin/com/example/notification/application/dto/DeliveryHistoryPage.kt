package com.example.notification.application.dto

import java.time.Instant
import java.util.UUID

/**
 * 사용자 알림 이력 페이지. cursor 페이지네이션 — 다음 페이지는 [nextCursor] 를 다시
 * 요청에 넣어 호출.
 *
 * nextCursor 가 null 이면 마지막 페이지.
 */
@JvmRecord
data class DeliveryHistoryPage(
    val items: List<Item>,
    val nextCursor: UUID?,
) {

    /** 한 알림 1줄 요약. attempt 별 상태는 별도 endpoint 에서 detail 로 조회. */
    @JvmRecord
    data class Item(
        val notificationId: UUID,
        val title: String,
        val kind: String,
        val status: String,
        val createdAt: Instant,
    )
}
