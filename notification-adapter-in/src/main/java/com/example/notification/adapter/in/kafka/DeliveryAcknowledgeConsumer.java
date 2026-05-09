package com.example.notification.adapter.in.kafka;

import com.example.notification.application.port.in.AcknowledgeDeliveryUseCase;
import com.example.notification.application.port.in.AcknowledgeDeliveryUseCase.AcknowledgeCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 별도 vendor 콜백 시스템이 있을 때, 우리 측 webhook 대신 Kafka 로 상태 변경 이벤트를
 * 넘겨받는 경로. 운영에선 vendor 별 형식이 다 달라 변환 layer 가 따로 있지만 여기선
 * 단순화된 JSON ({@code attemptId}, {@code success}, {@code vendorMessageId}, {@code reason}) 로 가정.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryAcknowledgeConsumer {

    private final AcknowledgeDeliveryUseCase ackUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "notification.delivery.ack",
            groupId = "${spring.kafka.consumer.group-id:notification-hub}-ack")
    public void onAck(String message) {
        try {
            AckPayload p = objectMapper.readValue(message, AckPayload.class);
            ackUseCase.acknowledge(
                    new AcknowledgeCommand(
                            UUID.fromString(p.attemptId()),
                            p.success(),
                            p.vendorMessageId(),
                            p.reason()));
        } catch (Exception e) {
            log.warn("malformed ack payload={}", message, e);
        }
    }

    public record AckPayload(
            String attemptId,
            boolean success,
            String vendorMessageId,
            String reason) {}
}
