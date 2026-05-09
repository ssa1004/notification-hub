package com.example.notification.adapter.out.template;

import com.example.notification.application.port.out.TemplateRenderer;
import com.example.notification.domain.template.Template;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Mustache 기반 템플릿 렌더링.
 *
 * <p>Mustache 의 placeholder 는 {@code {{var}}} 인데 우리 도메인 본문은 {@code {var}} 형식이라
 * 사용자 작성 편의를 위해 단일 중괄호를 이중 중괄호로 한 번 변환한 뒤 컴파일.
 *
 * <p>Thymeleaf / Freemarker 와 비교한 선택 배경은 ADR-0003 참조.
 */
@Component
public class MustacheTemplateRenderer implements TemplateRenderer {

    private final MustacheFactory factory = new DefaultMustacheFactory();

    @Override
    public Rendered render(Template template, Map<String, String> payload) {
        String title = renderOne(template.titleTemplate(), payload, "title");
        String body = renderOne(template.bodyTemplate(), payload, "body");
        return new Rendered(title, body);
    }

    private String renderOne(String tpl, Map<String, String> payload, String tag) {
        String mustacheized = tpl.replaceAll("\\{(\\w+)\\}", "{{$1}}");
        StringWriter w = new StringWriter();
        factory.compile(new StringReader(mustacheized), tag).execute(w, payload);
        return w.toString();
    }
}
