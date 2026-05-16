package com.example.notification.application.dto

import com.example.notification.domain.notification.NotificationKind

/**
 * SendNotificationUseCase 입력. 템플릿 사용 시 [templateKey] + [payload] (변수)
 * 만 채우고 title/body 는 비울 수 있음 — 그러면 템플릿 본문이 사용됨.
 *
 * raw 발송 (템플릿 미사용) 은 templateKey=null + title/body 직접 입력.
 *
 * payload 를 null 로 넘기면 빈 맵으로 정규화하므로 (Java caller contract 보존)
 * data class 가 아닌 일반 class + 직접 equals/hashCode.
 */
class SendNotificationCommand(
    idempotencyKey: String?,
    recipientId: String?,
    kind: NotificationKind?,
    title: String?,
    body: String?,
    payload: Map<String, String>?,
    templateKey: String?,
) {

    @get:JvmName("idempotencyKey")
    val idempotencyKey: String

    @get:JvmName("recipientId")
    val recipientId: String

    @get:JvmName("kind")
    val kind: NotificationKind

    @get:JvmName("title")
    val title: String?

    @get:JvmName("body")
    val body: String?

    @get:JvmName("payload")
    val payload: Map<String, String>

    @get:JvmName("templateKey")
    val templateKey: String?

    init {
        require(!idempotencyKey.isNullOrBlank()) { "idempotencyKey required" }
        require(!recipientId.isNullOrBlank()) { "recipientId required" }
        requireNotNull(kind) { "kind required" }
        require(!title.isNullOrBlank() || !templateKey.isNullOrBlank()) {
            "either title/body or templateKey required"
        }
        this.idempotencyKey = idempotencyKey
        this.recipientId = recipientId
        this.kind = kind
        this.title = title
        this.body = body
        this.payload = payload ?: emptyMap()
        this.templateKey = templateKey
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SendNotificationCommand) return false
        return idempotencyKey == other.idempotencyKey &&
            recipientId == other.recipientId &&
            kind == other.kind &&
            title == other.title &&
            body == other.body &&
            payload == other.payload &&
            templateKey == other.templateKey
    }

    override fun hashCode(): Int {
        var result = idempotencyKey.hashCode()
        result = 31 * result + recipientId.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (body?.hashCode() ?: 0)
        result = 31 * result + payload.hashCode()
        result = 31 * result + (templateKey?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "SendNotificationCommand(idempotencyKey=$idempotencyKey, recipientId=$recipientId, " +
            "kind=$kind, title=$title, body=$body, payload=$payload, templateKey=$templateKey)"
}
