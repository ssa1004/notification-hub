package com.example.notification.application.port.out

import com.example.notification.domain.template.Template

/**
 * 템플릿 + 변수 → 최종 텍스트. Mustache 등 구체 엔진은 adapter 측.
 */
interface TemplateRenderer {

    /**
     * @return [Rendered] 의 (renderedTitle, renderedBody)
     */
    fun render(template: Template, payload: Map<String, String>): Rendered

    @JvmRecord
    data class Rendered(val title: String, val body: String)
}
