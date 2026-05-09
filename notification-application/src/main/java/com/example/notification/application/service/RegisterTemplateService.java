package com.example.notification.application.service;

import com.example.notification.application.port.in.RegisterTemplateUseCase;
import com.example.notification.application.port.out.TemplateRepository;
import com.example.notification.domain.template.Template;
import com.example.notification.domain.template.TemplateKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterTemplateService implements RegisterTemplateUseCase {

    private final TemplateRepository repository;

    @Override
    @Transactional
    public Template register(RegisterCommand command) {
        Template template = Template.register(
                new TemplateKey(command.key()),
                command.locale(),
                command.channelType(),
                command.titleTemplate(),
                command.bodyTemplate());
        return repository.save(template);
    }
}
