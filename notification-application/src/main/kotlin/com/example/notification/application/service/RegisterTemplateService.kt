package com.example.notification.application.service

import com.example.notification.application.port.`in`.RegisterTemplateUseCase
import com.example.notification.application.port.`in`.RegisterTemplateUseCase.RegisterCommand
import com.example.notification.application.port.out.TemplateRepository
import com.example.notification.domain.template.Template
import com.example.notification.domain.template.TemplateKey
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RegisterTemplateService(
    private val repository: TemplateRepository,
) : RegisterTemplateUseCase {

    @Transactional
    override fun register(command: RegisterCommand): Template {
        val template = Template.register(
            TemplateKey(command.key),
            command.locale,
            command.channelType,
            command.titleTemplate,
            command.bodyTemplate,
        )
        return repository.save(template)
    }
}
