package com.example.notification.domain.notification

import com.example.notification.domain.recipient.RecipientId
import com.example.notification.domain.shared.IdempotencyKey
import java.time.Instant
import java.util.Collections
import java.util.UUID

/**
 * 알림 aggregate root. 한 발송 요청 = 한 Notification.
 *
 * 한 Notification 이 여러 `DeliveryAttempt` 로 fan-out 되며, 각 attempt 가 채널별로
 * 독립적으로 retry / DLQ / 성공 처리됩니다. Notification 자체는 fan-out 이 끝났는지만 추적.
 *
 * payload 는 템플릿 placeholder 의 변수 (예: `{name=홍길동, amount=10000}`) 또는
 * 자유 메타 데이터. 템플릿 미사용 알림은 title/body 가 그대로 사용됨.
 *
 * 전체 인자 생성자는 persistence reconstitution 용 (mapper 가 호출). 신규 생성은
 * companion 의 [accept] factory 사용.
 */
class Notification(
    id: UUID,
    idempotencyKey: IdempotencyKey,
    recipientId: RecipientId,
    kind: NotificationKind,
    title: String,
    body: String,
    payload: Map<String, String>?,
    templateKey: String?,
    createdAt: Instant,
    status: NotificationStatus,
) {

    @get:JvmName("id")
    val id: UUID = id

    @get:JvmName("idempotencyKey")
    val idempotencyKey: IdempotencyKey = idempotencyKey

    @get:JvmName("recipientId")
    val recipientId: RecipientId = recipientId

    @get:JvmName("kind")
    val kind: NotificationKind = kind

    @get:JvmName("title")
    val title: String = validateTitle(title)

    @get:JvmName("body")
    val body: String = validateBody(body)

    private val payload: Map<String, String> =
        if (payload == null) emptyMap() else java.util.Map.copyOf(payload)

    @get:JvmName("templateKey")
    val templateKey: String? = templateKey

    @get:JvmName("createdAt")
    val createdAt: Instant = createdAt

    @get:JvmName("status")
    var status: NotificationStatus = status
        private set

    @JvmName("payload")
    fun payload(): Map<String, String> = Collections.unmodifiableMap(payload)

    /** ACCEPTED → FANNED_OUT 전이. */
    fun markFannedOut() {
        check(status == NotificationStatus.ACCEPTED) {
            "fan-out only allowed from ACCEPTED, was $status"
        }
        status = NotificationStatus.FANNED_OUT
    }

    /** ACCEPTED → SUPPRESSED 전이 (발송 채널 0개). */
    fun markSuppressed() {
        check(status == NotificationStatus.ACCEPTED) {
            "suppress only allowed from ACCEPTED, was $status"
        }
        status = NotificationStatus.SUPPRESSED
    }

    /** FANNED_OUT → COMPLETED 전이 (모든 attempt 가 final). */
    fun markCompleted() {
        check(status == NotificationStatus.FANNED_OUT) {
            "complete only allowed from FANNED_OUT, was $status"
        }
        status = NotificationStatus.COMPLETED
    }

    companion object {
        /** 새 Notification 을 ACCEPTED 상태로 생성. */
        @JvmStatic
        fun accept(
            idempotencyKey: IdempotencyKey,
            recipientId: RecipientId,
            kind: NotificationKind,
            title: String,
            body: String,
            payload: Map<String, String>?,
            templateKey: String?,
        ): Notification =
            Notification(
                UUID.randomUUID(),
                idempotencyKey,
                recipientId,
                kind,
                title,
                body,
                if (payload == null) HashMap() else HashMap(payload),
                templateKey,
                Instant.now(),
                NotificationStatus.ACCEPTED,
            )

        private fun validateTitle(title: String): String {
            require(!title.isBlank() && title.length <= 200) { "title length 1..200 required" }
            return title
        }

        private fun validateBody(body: String): String {
            require(!body.isBlank() && body.length <= 4000) { "body length 1..4000 required" }
            return body
        }
    }
}
