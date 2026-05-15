package com.example.notification.domain.shared

/**
 * 같은 요청이 두 번 도착해도 한 번만 처리되게 만드는 멱등성 키.
 *
 * 호출자가 임의 문자열을 만들어 헤더 (`Idempotency-Key`) 로 전달합니다. 서버는 이 키를
 * Redis SETNX (키가 없을 때만 set, 있으면 실패하는 원자 연산) 로 점유한 뒤 use case 진입을
 * 허용합니다. TTL 24시간 — 같은 키 재사용 금지 기간.
 *
 * 생성자에서 trim 한 정규화 값을 보존하므로 equals/hashCode 는 data class 가 아니라 직접 구현.
 */
class IdempotencyKey(value: String) {

    @get:JvmName("value")
    val value: String = value.trim()

    init {
        require(this.value.isNotEmpty()) { "idempotencyKey must not be blank" }
        require(this.value.length <= MAX_LEN) { "idempotencyKey too long (max $MAX_LEN)" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IdempotencyKey) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        private const val MAX_LEN = 128
    }
}
