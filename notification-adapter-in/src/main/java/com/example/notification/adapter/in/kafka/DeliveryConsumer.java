package com.example.notification.adapter.in.kafka;

import com.example.notification.application.port.in.DispatchDeliveryUseCase;
import com.example.notification.domain.delivery.DeliveryRequested;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 채널별 Kafka topic 4개를 listen → 각 메시지의 attemptId 로 {@link DispatchDeliveryUseCase} 호출.
 *
 * <p>topic 분리 — 채널마다 partition / consumer-group / DLQ 정책 분리 가능. ADR-0002 참조.
 *
 * <p>실패 처리는 Spring Kafka 의 DefaultErrorHandler + DeadLetterPublishingRecoverer 가 담당
 * (bootstrap config 에서 등록). 여기서는 throw 만 하면 됨.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryConsumer {

    private final DispatchDeliveryUseCase dispatcher;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "notification.delivery.push",
            groupId = "${spring.kafka.consumer.group-id:notification-hub}-push")
    public void onPush(String message) {
        handle(message, "push");
    }

    @KafkaListener(
            topics = "notification.delivery.email",
            groupId = "${spring.kafka.consumer.group-id:notification-hub}-email")
    public void onEmail(String message) {
        handle(message, "email");
    }

    @KafkaListener(
            topics = "notification.delivery.sms",
            groupId = "${spring.kafka.consumer.group-id:notification-hub}-sms")
    public void onSms(String message) {
        handle(message, "sms");
    }

    @KafkaListener(
            topics = "notification.delivery.kakao_alimtalk",
            groupId = "${spring.kafka.consumer.group-id:notification-hub}-kakao")
    public void onKakao(String message) {
        handle(message, "kakao");
    }

    private void handle(String json, String channel) {
        DeliveryRequested event;
        try {
            event = objectMapper.readValue(json, DeliveryRequested.class);
        } catch (Exception e) {
            log.warn("malformed delivery event channel={} payload={}", channel, json, e);
            return; // poison message — 일반 retry 의미 없음. DLQ 도 굳이 보낼 필요 없음.
        }
        dispatcher.dispatch(event.deliveryAttemptId());
    }
}
