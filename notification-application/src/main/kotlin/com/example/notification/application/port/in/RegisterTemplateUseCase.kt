package com.example.notification.application.port.`in`

import com.example.notification.domain.channel.ChannelType
import com.example.notification.domain.shared.Locale
import com.example.notification.domain.template.Template

/** 운영자 (또는 신뢰된 시스템) 가 템플릿 등록. (key, locale, channelType) 조합이 unique. */
interface RegisterTemplateUseCase {

    fun register(command: RegisterCommand): Template

    @JvmRecord
    data class RegisterCommand(
        val key: String,
        val locale: Locale,
        val channelType: ChannelType,
        val titleTemplate: String,
        val bodyTemplate: String,
    )
}
