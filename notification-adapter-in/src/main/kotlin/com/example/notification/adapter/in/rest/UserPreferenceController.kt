package com.example.notification.adapter.`in`.rest

import com.example.notification.adapter.`in`.rest.dto.UpdateUserPreferenceRequest
import com.example.notification.application.port.`in`.UpdateUserPreferenceUseCase
import com.example.notification.domain.preference.UserPreference
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserPreferenceController(
    private val updateUseCase: UpdateUserPreferenceUseCase,
) {

    @PutMapping("/{recipientId}/preferences")
    fun update(
        @PathVariable("recipientId") @NotBlank recipientId: String,
        @Valid @RequestBody request: UpdateUserPreferenceRequest,
    ): PreferenceResponse {
        val saved = updateUseCase.update(request.toCommand(recipientId))
        return PreferenceResponse.from(saved)
    }

    @JvmRecord
    data class PreferenceResponse(
        val recipientId: String,
        val quietStart: String?,
        val quietEnd: String?,
        val timezone: String,
    ) {
        companion object {
            @JvmStatic
            fun from(p: UserPreference): PreferenceResponse =
                PreferenceResponse(
                    p.recipientId.value,
                    p.quietHours?.start?.toString(),
                    p.quietHours?.end?.toString(),
                    p.timezone.id,
                )
        }
    }
}
