package com.example.notification.adapter.in.rest;

import com.example.notification.application.dto.DlqEntryView;
import com.example.notification.application.port.in.DlqAdminUseCase;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DLQ (EXHAUSTED) 운영 endpoint. {@link com.example.notification.adapter.in.security.AdminAuthFilter}
 * 가 요청별로 admin role 검증 → 통과하면 use case 가 실행. 비-admin 호출은 use case 단에서 401.
 */
@RestController
@RequestMapping("/api/v1/admin/dlq")
@RequiredArgsConstructor
public class AdminDlqController {

    private final DlqAdminUseCase useCase;

    /** EXHAUSTED 항목 cursor 페이지네이션. limit 1~200. */
    @GetMapping
    public List<DlqEntryView> list(
            @RequestParam(value = "cursor", required = false) UUID cursor,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return useCase.list(cursor, limit);
    }

    /** 한 항목 재발송 — retry=0 으로 환원 + Outbox 재발행. */
    @PostMapping("/{attemptId}/replay")
    public DlqEntryView replay(@PathVariable("attemptId") UUID attemptId) {
        return useCase.replay(attemptId);
    }

    /** 한 항목 영구 종료 — PERMANENTLY_FAILED 로 마킹. body 의 reason 는 audit 에 기록. */
    @PostMapping("/{attemptId}/discard")
    public DlqEntryView discard(
            @PathVariable("attemptId") UUID attemptId,
            @RequestBody(required = false) DiscardRequest request) {
        String reason = request == null ? null : request.reason();
        return useCase.discard(attemptId, reason);
    }

    public record DiscardRequest(@Size(max = 256) String reason) {}
}
