package com.example.notification.application.port.out;

import com.example.notification.domain.template.Template;
import java.util.Map;

/**
 * 템플릿 + 변수 → 최종 텍스트. Mustache 등 구체 엔진은 adapter 측.
 */
public interface TemplateRenderer {

    /**
     * @return [renderedTitle, renderedBody]
     */
    Rendered render(Template template, Map<String, String> payload);

    record Rendered(String title, String body) {}
}
