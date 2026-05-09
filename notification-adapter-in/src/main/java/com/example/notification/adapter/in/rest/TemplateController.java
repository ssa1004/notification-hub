package com.example.notification.adapter.in.rest;

import com.example.notification.adapter.in.rest.dto.RegisterTemplateRequest;
import com.example.notification.application.port.in.RegisterTemplateUseCase;
import com.example.notification.domain.template.Template;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final RegisterTemplateUseCase registerUseCase;

    @PostMapping
    public ResponseEntity<TemplateResponse> register(
            @Valid @RequestBody RegisterTemplateRequest request) {
        Template t = registerUseCase.register(request.toCommand());
        return ResponseEntity.ok(TemplateResponse.from(t));
    }

    public record TemplateResponse(
            UUID id,
            String key,
            String locale,
            String channelType,
            String titleTemplate,
            String bodyTemplate) {
        static TemplateResponse from(Template t) {
            return new TemplateResponse(
                    t.id(),
                    t.key().value(),
                    t.locale().tag(),
                    t.channelType().name(),
                    t.titleTemplate(),
                    t.bodyTemplate());
        }
    }
}
