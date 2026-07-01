package com.example.notification.adapter.out.template

import com.example.notification.application.port.out.TemplateRenderer
import com.example.notification.domain.template.Template
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import java.io.StringReader
import java.io.StringWriter
import org.springframework.stereotype.Component

/**
 * Mustache 기반 템플릿 렌더링.
 *
 * Mustache 의 placeholder 는 `{{var}}` 인데 우리 도메인 본문은 `{var}` 형식이라
 * 사용자 작성 편의를 위해 단일 중괄호를 이중 중괄호로 한 번 변환한 뒤 컴파일.
 *
 * Thymeleaf / Freemarker 와 비교한 선택 배경은 ADR-0003 참조.
 */
@Component
class MustacheTemplateRenderer : TemplateRenderer {

    // SMS/PUSH/KAKAO 는 평문 채널이라 HTML 이스케이프를 끈다. 기본 factory 는 {{var}} 치환 시
    // &,<,>,\" 를 이스케이프해 payload 값을 훼손한다(예: "Tom & Jerry" → "Tom &amp; Jerry").
    private val factory: MustacheFactory = object : DefaultMustacheFactory() {
        override fun encode(value: String, writer: java.io.Writer) {
            writer.write(value)
        }
    }

    override fun render(template: Template, payload: Map<String, String>): TemplateRenderer.Rendered {
        val title = renderOne(template.titleTemplate, payload, "title")
        val body = renderOne(template.bodyTemplate, payload, "body")
        return TemplateRenderer.Rendered(title, body)
    }

    private fun renderOne(tpl: String, payload: Map<String, String>, tag: String): String {
        val mustacheized = tpl.replace(SINGLE_BRACE, "{{$1}}")
        val w = StringWriter()
        factory.compile(StringReader(mustacheized), tag).execute(w, payload)
        return w.toString()
    }

    companion object {
        private val SINGLE_BRACE = Regex("\\{(\\w+)\\}")
    }
}
