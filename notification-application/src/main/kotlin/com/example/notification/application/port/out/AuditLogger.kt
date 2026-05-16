package com.example.notification.application.port.out

/**
 * 모든 알림 발송 시도가 한 줄로 audit log 에 기록되는 append-only port.
 *
 * compliance / 사용자 문의 대응 (왜 안 왔는지 / 두 번 왔는지) 에 필수. ADR-0023 결정 톤
 * 따라 별도 테이블 + soft delete 금지.
 */
interface AuditLogger {

    /**
     * @param actor 행위자 (사용자 id 또는 system 식별자)
     * @param action e.g. `"NOTIFICATION_ACCEPTED"`, `"DELIVERY_DISPATCHED"`
     * @param data 추가 컨텍스트 (PII 는 마스킹된 채로)
     */
    fun log(actor: String, action: String, data: Map<String, *>)
}
