package com.example.notification.domain.shared

/**
 * 사용자 언어 / 지역. 템플릿 본문이 locale 별로 다르므로 템플릿 조회 키의 일부.
 *
 * BCP-47 lowercase 형식 (예: `ko-kr`, `en-us`). 시스템 기본 fallback 은 `ko-kr`.
 *
 * 생성자에서 정규화 (trim + lowercase) 한 값을 보존하므로 equals/hashCode 는 직접 구현.
 */
class Locale(tag: String) {

    @get:JvmName("tag")
    val tag: String = tag.trim().lowercase()

    init {
        require(this.tag.matches(TAG_PATTERN)) { "invalid locale tag: $tag" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Locale) return false
        return tag == other.tag
    }

    override fun hashCode(): Int = tag.hashCode()

    override fun toString(): String = tag

    companion object {
        // KO_KR / EN_US 보다 먼저 초기화되어야 함 — 아래 상수 생성자가 이 패턴을 참조.
        private val TAG_PATTERN = Regex("^[a-z]{2}(-[a-z0-9]{2,8})?$")

        @JvmField
        val KO_KR: Locale = Locale("ko-kr")

        @JvmField
        val EN_US: Locale = Locale("en-us")
    }
}
