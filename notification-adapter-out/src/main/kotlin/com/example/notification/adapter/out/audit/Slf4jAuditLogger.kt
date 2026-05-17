package com.example.notification.adapter.out.audit

import com.example.notification.adapter.out.persistence.mapper.JsonMapper
import com.example.notification.application.port.out.AuditLogger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 단순 SLF4J append-only audit. 운영에선 Kafka topic / 별도 DB 테이블로 sink 분리.
 *
 * 여기선 학습 단계라 INFO 레벨로 별도 logger 이름 (`audit`) 으로 출력 — 운영에선
 * appender 를 따로 잡아 audit 만 별도 파일/저장소에 보냄.
 */
@Component
class Slf4jAuditLogger : AuditLogger {

    private val log = LoggerFactory.getLogger("audit")

    override fun log(actor: String, action: String, data: Map<String, *>) {
        log.info(
            "AUDIT actor={} action={} data={}",
            actor,
            action,
            JsonMapper.writeMap(data),
        )
    }
}
