package com.example.notification.domain.delivery;

/**
 * 한 채널별 발송 시도의 상태.
 *
 * <p>상태 머신:
 *
 * <pre>
 *   PENDING ──▶ DISPATCHING ──▶ SUCCEEDED
 *                  │
 *                  ├──▶ FAILED ──(retry < max)──▶ PENDING
 *                  │
 *                  └──▶ FAILED ──(retry == max)──▶ EXHAUSTED  (DLQ)
 * </pre>
 */
public enum DeliveryStatus {
    /** 생성 직후 / 재시도 대기. Outbox 에 적재된 상태. */
    PENDING,
    /** Worker 가 vendor 에 호출 중. */
    DISPATCHING,
    /** vendor 응답 200 OK 또는 콜백으로 성공 알림 받음. */
    SUCCEEDED,
    /** vendor 호출 실패 (재시도 가능 단계). */
    FAILED,
    /** 재시도 횟수 초과 → DLQ 로 이동. 운영자가 수동으로만 재처리. */
    EXHAUSTED
}
