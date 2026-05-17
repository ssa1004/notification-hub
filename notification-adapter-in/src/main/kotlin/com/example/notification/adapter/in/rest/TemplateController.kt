package com.example.notification.adapter.`in`.rest

import com.example.notification.adapter.`in`.rest.dto.RegisterTemplateRequest
import com.example.notification.application.port.`in`.RegisterTemplateUseCase
import com.example.notification.domain.template.Template
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/templates")
class TemplateController(
    private val registerUseCase: RegisterTemplateUseCase,
) {

    @PostMapping
    fun register(
        @Valid @RequestBody request: RegisterTemplateRequest,
    ): ResponseEntity<TemplateResponse> {
        val t = registerUseCase.register(request.toCommand())
        return ResponseEntity.ok(TemplateResponse.from(t))
    }

    @JvmRecord
    data class TemplateResponse(
        val id: UUID,
        val key: String,
        val locale: String,
        val channelType: String,
        val titleTemplate: String,
        val bodyTemplate: String,
    ) {
        companion object {
            @JvmStatic
            fun from(t: Template): TemplateResponse =
                TemplateResponse(
                    t.id,
                    t.key.value,
                    t.locale.tag,
                    t.channelType.name,
                    t.titleTemplate,
                    t.bodyTemplate,
                )
        }
    }
}
