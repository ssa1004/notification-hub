package com.example.notification.domain.template

import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.shared.Locale
import java.time.Instant
import java.util.UUID

/**
 * 다국어 알림 템플릿. (key, locale, channelType) 가 unique.
 *
 * 본문은 Mustache placeholder 를 포함합니다 — `안녕하세요 {name} 님, 결제 {amount}원이
 * 처리되었습니다`. 실제 치환은 use case 에서 `TemplateRenderer` 포트가 담당.
 *
 * 채널별 본문이 다를 수 있습니다 — SMS 는 90B 짧은 텍스트, 이메일은 풀 HTML. 따라서 같은 key
 * + locale 안에서도 channelType 이 다르면 별도 row.
 */
class Template(
    id: UUID,
    key: TemplateKey,
    locale: Locale,
    channelType: ChannelType,
    titleTemplate: String,
    bodyTemplate: String,
    createdAt: Instant,
) {

    @get:JvmName("id")
    val id: UUID = id

    @get:JvmName("key")
    val key: TemplateKey = key

    @get:JvmName("locale")
    val locale: Locale = locale

    @get:JvmName("channelType")
    val channelType: ChannelType = channelType

    @get:JvmName("titleTemplate")
    val titleTemplate: String = titleTemplate

    @get:JvmName("bodyTemplate")
    val bodyTemplate: String = bodyTemplate

    @get:JvmName("createdAt")
    val createdAt: Instant = createdAt

    init {
        require(!titleTemplate.isBlank() && titleTemplate.length <= 200) {
            "titleTemplate length 1..200 required"
        }
        require(!bodyTemplate.isBlank() && bodyTemplate.length <= 4000) {
            "bodyTemplate length 1..4000 required"
        }
        validateChannelLimit(channelType, bodyTemplate)
    }

    /**
     * 변수 셋이 본문 placeholder 를 모두 충족하는지 사전 검증.
     *
     * `{name}` 같은 single-tag placeholder 만 검사 (Mustache 의 sections 는 검사 X).
     * 누락 시 빈 문자열로 치환되는 vendor 도 있어 일찍 막는 편이 안전.
     */
    fun verifyPayloadCovers(payload: Map<String, String>) {
        for (match in PLACEHOLDER_PATTERN.findAll(bodyTemplate + titleTemplate)) {
            val variable = match.groupValues[1]
            require(payload.containsKey(variable)) {
                "template payload missing variable: $variable (template=$key)"
            }
        }
    }

    companion object {
        private val PLACEHOLDER_PATTERN = Regex("\\{(\\w+)\\}")

        @JvmStatic
        fun register(
            key: TemplateKey,
            locale: Locale,
            channelType: ChannelType,
            titleTemplate: String,
            bodyTemplate: String,
        ): Template =
            Template(
                UUID.randomUUID(),
                key,
                locale,
                channelType,
                titleTemplate,
                bodyTemplate,
                Instant.now(),
            )

        /**
         * 치환 후 길이가 채널별 한도를 분명히 넘을 수 있는 경우만 사전 차단.
         *
         * SMS 한국 LMS = 2000B 이고 SMS = 90B. placeholder 가 있으면 정확한 길이를 알 수 없으므로
         * 여기서는 템플릿 raw 길이로 sanity check 만. 실제 치환 후 길이 검증은 vendor 가 함.
         */
        private fun validateChannelLimit(type: ChannelType, body: String) {
            require(!(type == ChannelType.SMS && body.length > 2000)) {
                "SMS body must be <= 2000 chars (LMS limit)"
            }
        }
    }
}
