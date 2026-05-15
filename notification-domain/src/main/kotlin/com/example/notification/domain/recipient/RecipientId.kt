package com.example.notification.domain.recipient

/**
 * 수신자 (사용자) 식별자. 외부 시스템 (auth / member service) 의 user id 와 동일.
 *
 * UUID 또는 임의 문자열을 허용 — 다양한 외부 시스템과의 호환을 위해 형식을 강제하지 않음.
 *
 * 생성자에서 trim 한 정규화 값을 보존하므로 equals/hashCode 는 직접 구현.
 */
class RecipientId(value: String) {

    @get:JvmName("value")
    val value: String = value.trim()

    init {
        require(this.value.isNotEmpty()) { "recipientId must not be blank" }
        require(this.value.length <= MAX_LEN) { "recipientId too long" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecipientId) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        private const val MAX_LEN = 128
    }
}
