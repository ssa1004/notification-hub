package com.example.notification.domain.template

/**
 * 템플릿 식별자. 운영자가 등록한 템플릿을 use case 가 참조할 때 쓰는 안정 키.
 *
 * 예: `order.shipped.v1`, `auth.otp.v2`.
 *
 * 관례: `<도메인>.<이벤트>.v<버전>`. 버전이 바뀌면 새 row 로 등록.
 *
 * 생성자에서 trim 한 정규화 값을 보존하므로 equals/hashCode 는 직접 구현.
 */
class TemplateKey(value: String) {

    @get:JvmName("value")
    val value: String = value.trim()

    init {
        require(this.value.matches(KEY_PATTERN)) {
            "templateKey must match `^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$`: $value"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TemplateKey) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        private val KEY_PATTERN = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
    }
}
