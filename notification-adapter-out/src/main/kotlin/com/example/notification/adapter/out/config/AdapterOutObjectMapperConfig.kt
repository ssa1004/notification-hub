package com.example.notification.adapter.out.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AdapterOutObjectMapperConfig {

    /**
     * 도메인 이벤트의 Instant 직렬화를 위해 JavaTimeModule 등록. Spring Boot 가 같은 빈을
     * autoconfig 로 등록하지만 어댑터 단독 테스트 (slice) 에서도 사용할 수 있게 명시적으로 둠.
     */
    @Bean
    fun adapterOutObjectMapper(): ObjectMapper {
        val m = ObjectMapper()
        m.registerModule(JavaTimeModule())
        m.findAndRegisterModules()
        return m
    }
}
