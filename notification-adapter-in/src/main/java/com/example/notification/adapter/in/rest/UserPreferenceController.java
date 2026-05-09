package com.example.notification.adapter.in.rest;

import com.example.notification.adapter.in.rest.dto.UpdateUserPreferenceRequest;
import com.example.notification.application.port.in.UpdateUserPreferenceUseCase;
import com.example.notification.domain.preference.UserPreference;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UpdateUserPreferenceUseCase updateUseCase;

    @PutMapping("/{recipientId}/preferences")
    public PreferenceResponse update(
            @PathVariable("recipientId") @NotBlank String recipientId,
            @Valid @RequestBody UpdateUserPreferenceRequest request) {
        UserPreference saved = updateUseCase.update(request.toCommand(recipientId));
        return PreferenceResponse.from(saved);
    }

    public record PreferenceResponse(
            String recipientId,
            String quietStart,
            String quietEnd,
            String timezone) {

        static PreferenceResponse from(UserPreference p) {
            return new PreferenceResponse(
                    p.recipientId().value(),
                    p.quietHours() == null ? null : p.quietHours().start().toString(),
                    p.quietHours() == null ? null : p.quietHours().end().toString(),
                    p.timezone().getId());
        }
    }
}
