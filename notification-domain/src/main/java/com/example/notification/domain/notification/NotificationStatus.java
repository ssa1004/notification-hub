package com.example.notification.domain.notification;

/**
 * 알림 전체 (=묶음 단위) 의 상태. 한 알림은 여러 채널로 fan-out 되므로 *대표 상태* 입니다.
 *
 * <p>상태 머신:
 *
 * <pre>
 *   ACCEPTED ──┬──▶ FANNED_OUT ──▶ COMPLETED   (모든 DeliveryAttempt 가 final)
 *              └──▶ SUPPRESSED                 (선호도/DND/opt-out 으로 발송 채널 0)
 *
 *   COMPLETED 의 종류:
 *     - 모두 성공 → DeliveryAttempt 단위로 SUCCEEDED
 *     - 일부/전부 실패 → DeliveryAttempt 단위로 FAILED 또는 EXHAUSTED 보존
 *     - notification 자체는 fan-out 이 끝났다는 의미만 가짐
 * </pre>
 */
public enum NotificationStatus {
    /** Use case 진입, idempotency 통과, 도메인 모델 생성 직후. */
    ACCEPTED,
    /** 발송 채널 결정 + DeliveryAttempt 생성 + Outbox 적재 완료. */
    FANNED_OUT,
    /** 모든 DeliveryAttempt 가 최종 상태 (SUCCEEDED / FAILED / EXHAUSTED). */
    COMPLETED,
    /** 사용자 opt-out 또는 DND 로 발송 채널 0개 — fan-out 자체를 안 함. */
    SUPPRESSED
}
