package com.example.notification.adapter.out.audit;

import com.example.notification.application.port.out.AuditLogger;
import com.example.notification.adapter.out.persistence.mapper.JsonMapper;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 단순 SLF4J append-only audit. 운영에선 Kafka topic / 별도 DB 테이블로 sink 분리.
 *
 * <p>여기선 학습 단계라 INFO 레벨로 별도 logger 이름 ({@code audit}) 으로 출력 — 운영에선
 * appender 를 따로 잡아 audit 만 별도 파일/저장소에 보냄.
 */
@Slf4j(topic = "audit")
@Component
public class Slf4jAuditLogger implements AuditLogger {

    @Override
    public void log(String actor, String action, Map<String, ?> data) {
        log.info(
                "AUDIT actor={} action={} data={}",
                actor,
                action,
                JsonMapper.writeMap(data));
    }
}
