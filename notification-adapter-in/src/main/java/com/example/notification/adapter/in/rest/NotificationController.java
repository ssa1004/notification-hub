package com.example.notification.adapter.in.rest;

import com.example.notification.adapter.in.rest.dto.SendNotificationRequest;
import com.example.notification.adapter.in.rest.dto.SendNotificationResponse;
import com.example.notification.application.dto.DeliveryHistoryPage;
import com.example.notification.application.dto.SendNotificationResult;
import com.example.notification.application.port.in.ListMyDeliveriesUseCase;
import com.example.notification.application.port.in.SendNotificationUseCase;
import com.example.notification.domain.notification.NotificationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final SendNotificationUseCase sendUseCase;
    private final ListMyDeliveriesUseCase listUseCase;

    /**
     * 알림 전송. {@code Idempotency-Key} 헤더 필수 — 같은 키 재요청은 409.
     *
     * <p>응답 코드:
     * <ul>
     *   <li>202 Accepted — fan-out 됨 (vendor 호출은 비동기)
     *   <li>200 OK + status=SUPPRESSED — opt-out / DND 로 발송 안 함
     *   <li>409 — 멱등성 키 중복
     *   <li>404 — recipient 없음
     *   <li>429 — rate limit 초과
     * </ul>
     */
    @PostMapping
    public ResponseEntity<SendNotificationResponse> send(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody SendNotificationRequest request) {
        SendNotificationResult result = sendUseCase.send(request.toCommand(idempotencyKey));
        HttpStatus status =
                result.status() == NotificationStatus.SUPPRESSED
                        ? HttpStatus.OK
                        : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(SendNotificationResponse.from(result));
    }

    /** 내 알림 이력. cursor 페이지네이션. */
    @GetMapping("/me")
    public DeliveryHistoryPage list(
            @RequestParam("recipientId") @NotBlank String recipientId,
            @RequestParam(value = "cursor", required = false) UUID cursor,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return listUseCase.list(recipientId, cursor, limit);
    }

    /** 단건 조회 — operational debug. */
    @GetMapping("/{id}")
    public ResponseEntity<UUID> get(@PathVariable("id") UUID id) {
        // 단순화: id 조회는 별도 use case 없이 응답만 echo. 실제론 NotificationDetailUseCase 추가.
        return ResponseEntity.ok(id);
    }
}
