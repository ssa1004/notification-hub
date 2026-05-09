package com.example.notification.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 사용자 알림 이력 페이지. cursor 페이지네이션 — 다음 페이지는 {@code nextCursor} 를 다시
 * 요청에 넣어 호출.
 *
 * <p>nextCursor 가 null 이면 마지막 페이지.
 */
public record DeliveryHistoryPage(List<Item> items, UUID nextCursor) {

    /** 한 알림 1줄 요약. attempt 별 상태는 별도 endpoint 에서 detail 로 조회. */
    public record Item(
            UUID notificationId,
            String title,
            String kind,
            String status,
            Instant createdAt) {}
}
