package com.example.notification.adapter.`in`.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI 문서 메타데이터를 한 곳에 고정한다.
 *
 * 결정론(determinism) 이 핵심 — springdoc 기본 동작은 `info.title=OpenAPI definition`,
 * `info.version=v0`, 그리고 `servers` 에 런타임 `server.port` 를 박아 넣은 URL
 * (예: `http://localhost:18099`) 을 생성한다. 그러면 산출 spec 이 부팅 포트에 의존하게 되어,
 * CI 의 spec drift gate (`git diff --exit-code docs/openapi/notification-hub.yaml`) 가
 * 포트만 달라도 헛되이 실패한다.
 *
 * 여기서 `info` 와 `servers` 를 명시 고정하면 어느 포트로 띄워도 동일한 spec 이 나와,
 * drift gate 가 "실제 API 표면 변화" 만 잡는다.
 */
@Configuration
internal class OpenApiConfig {

    @Bean
    open fun notificationHubOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Notification Hub API")
                    .version("0.1.0")
                    .description(
                        "Multi-channel (PUSH/EMAIL/SMS/KAKAO) notification delivery service.",
                    )
                    .license(License().name("MIT").url("https://opensource.org/licenses/MIT")),
            )
            .addServersItem(
                Server()
                    .url("https://api.notification-hub.example.com")
                    .description("Canonical base URL (placeholder)"),
            )
}
