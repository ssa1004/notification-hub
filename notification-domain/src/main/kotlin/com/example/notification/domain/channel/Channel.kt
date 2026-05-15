package com.example.notification.domain.channel

import java.util.Objects

/**
 * 한 채널을 식별하는 VO. 사용자 1명이 같은 채널을 여러 개 가질 수도 있어
 * (예: 회사 이메일 + 개인 이메일) [address] 까지 묶여야 정확한 식별이 됩니다.
 *
 * 각 vendor 의 raw address 형식:
 * - `PUSH`: FCM device token (보통 152~163 chars)
 * - `EMAIL`: RFC 5322 email
 * - `SMS` / `KAKAO_ALIMTALK`: E.164 phone number (예: +821012345678)
 *
 * 도메인은 형식 검증까지만 책임지고 실제 도달 가능 여부는 vendor 가 판단합니다.
 *
 * 생성자에서 trim 한 정규화 값을 보존하므로 equals/hashCode 는 직접 구현.
 */
class Channel(type: ChannelType, address: String) {

    @get:JvmName("type")
    val type: ChannelType = type

    @get:JvmName("address")
    val address: String

    init {
        val trimmed = address.trim()
        require(trimmed.isNotEmpty()) { "address must not be blank" }
        validate(type, trimmed)
        this.address = trimmed
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Channel) return false
        return type == other.type && address == other.address
    }

    override fun hashCode(): Int = Objects.hash(type, address)

    override fun toString(): String = "$type:${maskAddress()}"

    /** 로그 / audit 에 raw address 가 그대로 찍히지 않도록 일부 마스킹. */
    private fun maskAddress(): String {
        if (address.length <= 4) return "****"
        return address.substring(0, 2) + "***" + address.substring(address.length - 2)
    }

    companion object {
        private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        private val PHONE_PATTERN = Regex("^\\+\\d{8,15}$")

        private fun validate(type: ChannelType, address: String) {
            when (type) {
                ChannelType.EMAIL ->
                    require(address.matches(EMAIL_PATTERN)) { "invalid email: $address" }

                ChannelType.SMS, ChannelType.KAKAO_ALIMTALK ->
                    require(address.matches(PHONE_PATTERN)) {
                        "invalid phone (E.164 expected): $address"
                    }

                ChannelType.PUSH ->
                    require(address.length in 32..256) {
                        "invalid push token length: ${address.length}"
                    }
            }
        }
    }
}
