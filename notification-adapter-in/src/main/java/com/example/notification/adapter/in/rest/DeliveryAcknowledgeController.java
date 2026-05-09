package com.example.notification.adapter.in.rest;

import com.example.notification.adapter.in.rest.dto.AcknowledgeDeliveryRequest;
import com.example.notification.application.port.in.AcknowledgeDeliveryUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * vendor 측 webhook 수신용 endpoint. 운영에선 vendor 별 별도 endpoint + signature 검증 추가.
 */
@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryAcknowledgeController {

    private final AcknowledgeDeliveryUseCase ackUseCase;

    @PostMapping("/{deliveryAttemptId}/ack")
    public ResponseEntity<Void> acknowledge(
            @PathVariable("deliveryAttemptId") UUID deliveryAttemptId,
            @Valid @RequestBody AcknowledgeDeliveryRequest request) {
        ackUseCase.acknowledge(request.toCommand(deliveryAttemptId));
        return ResponseEntity.accepted().build();
    }
}
