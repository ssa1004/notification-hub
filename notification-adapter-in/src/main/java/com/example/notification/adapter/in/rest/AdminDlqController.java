package com.example.notification.adapter.in.rest;

import com.example.notification.application.dto.DlqBulkJob;
import com.example.notification.application.dto.DlqBulkResult;
import com.example.notification.application.dto.DlqEntryDetail;
import com.example.notification.application.dto.DlqEntryFilter;
import com.example.notification.application.dto.DlqEntryView;
import com.example.notification.application.dto.DlqListPage;
import com.example.notification.application.dto.DlqStats;
import com.example.notification.application.exception.AttemptNotFoundException;
import com.example.notification.application.exception.RateLimitExceededException;
import com.example.notification.application.port.in.DlqAdminUseCase;
import com.example.notification.application.port.in.DlqBulkAdminUseCase;
import com.example.notification.application.port.out.AdminRateLimiter;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.shared.RateLimitDecision;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 *
 * <p>모든 endpoint 는 호출자 IP 별 admin rate limit (분당 60). 초과 시 429 + Retry-After.
 *
 * <p>ADR-0012 (초기 단건 API) + ADR-0015 (filter / bulk / stats 확장) 참조. 기존 endpoint 는
 * 호환을 위해 그대로 유지.
 */
@RestController
@RequestMapping("/api/v1/admin/dlq")
@RequiredArgsConstructor
public class AdminDlqController {

    static final String RL_SCOPE_READ = "dlq.read";
    static final String RL_SCOPE_WRITE = "dlq.write";
    static final String RL_SCOPE_BULK = "dlq.bulk";

    private final DlqAdminUseCase useCase;
    private final DlqBulkAdminUseCase bulkUseCase;
    private final AdminRateLimiter adminRateLimiter;

    // ============================================================
    // 기존 (ADR-0012). 호환 유지.
    // ============================================================

    /**
     * EXHAUSTED 항목 cursor 페이지네이션. limit 1~200.
     *
     * <p>호환 endpoint — {@code ?topic=&channel=&...} 같은 확장 필터는 {@link #search}
     * (별 path {@code /search}) 에서 받음.
     */
    @GetMapping
    public List<DlqEntryView> list(
            @RequestParam(value = "cursor", required = false) UUID cursor,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            HttpServletRequest request) {
        rateLimit(RL_SCOPE_READ, request);
        return useCase.list(cursor, limit);
    }

    /** 한 항목 재발송 — retry=0 으로 환원 + Outbox 재발행. */
    @PostMapping("/{attemptId}/replay")
    public DlqEntryView replay(
            @PathVariable("attemptId") UUID attemptId, HttpServletRequest request) {
        rateLimit(RL_SCOPE_WRITE, request);
        return useCase.replay(attemptId);
    }

    /** 한 항목 영구 종료 — PERMANENTLY_FAILED 로 마킹. body 의 reason 는 audit 에 기록. */
    @PostMapping("/{attemptId}/discard")
    public DlqEntryView discard(
            @PathVariable("attemptId") UUID attemptId,
            @RequestBody(required = false) DiscardRequest body,
            HttpServletRequest request) {
        rateLimit(RL_SCOPE_WRITE, request);
        String reason = body == null ? null : body.reason();
        return useCase.discard(attemptId, reason);
    }

    public record DiscardRequest(@Size(max = 256) String reason) {}

    // ============================================================
    // 확장 (ADR-0015).
    // ============================================================

    /**
     * filter 조건으로 검색 (cursor 페이지네이션).
     *
     * <p>{@code GET /api/v1/admin/dlq/search?channel=PUSH&topic=&consumerGroup=&from=&to=&
     * errorType=&cursor=&size=}.
     *
     * <p>{@code channel} 과 {@code topic} 모두 주면 {@code channel} 우선. {@code from}/{@code to}
     * 는 ISO-8601 instant (예: {@code 2026-05-15T00:00:00Z}). {@code size} 는 1~200 캡.
     */
    @GetMapping("/search")
    public DlqListPage search(
            @RequestParam(value = "channel", required = false) ChannelType channel,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "consumerGroup", required = false) String consumerGroup,
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to", required = false) Instant to,
            @RequestParam(value = "errorType", required = false) String errorType,
            @RequestParam(value = "cursor", required = false) UUID cursor,
            @RequestParam(value = "size", defaultValue = "50") int size,
            HttpServletRequest request) {
        rateLimit(RL_SCOPE_READ, request);
        DlqEntryFilter filter =
                new DlqEntryFilter(channel, topic, consumerGroup, from, to, errorType);
        return useCase.search(filter, cursor, size);
    }

    /** 단건 detail — payload 전체 + retry context. 없으면 404. */
    @GetMapping("/{attemptId}")
    public DlqEntryDetail detail(
            @PathVariable("attemptId") UUID attemptId, HttpServletRequest request) {
        rateLimit(RL_SCOPE_READ, request);
        return useCase.detail(attemptId)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));
    }

    /**
     * 시간 bucket 별 stats. {@code GET /api/v1/admin/dlq/stats?from=&to=&bucket=PT1H}. bucket 은
     * ISO-8601 duration (예: {@code PT1H}, {@code PT15M}). 기본 1시간, 범위 기본 최근 24h.
     */
    @GetMapping("/stats")
    public DlqStats stats(
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to", required = false) Instant to,
            @RequestParam(value = "bucket", required = false) Duration bucket,
            HttpServletRequest request) {
        rateLimit(RL_SCOPE_READ, request);
        return useCase.stats(from, to, bucket);
    }

    /**
     * bulk replay. {@code confirm=true} 일 때만 실행, 그 외에는 dry-run (대상 개수 + sample id).
     * 이는 안전 가드 — confirm 없는 호출이 곧바로 수천 건을 재발송하지 못하게 한다.
     *
     * <p>응답: dry-run 이면 {@code mode=DRY_RUN}, 실 실행이면 {@code mode=EXECUTING + jobId}.
     */
    @PostMapping("/bulk-replay")
    public DlqBulkResult bulkReplay(
            @Valid @RequestBody BulkRequest body, HttpServletRequest request) {
        rateLimit(RL_SCOPE_BULK, request);
        return bulkUseCase.bulkReplay(body.toFilter(), body.confirmedOrDefault(), body.reason());
    }

    /**
     * bulk discard — reason 필수 (body 의 {@code reason}). dry-run 의미는 {@link #bulkReplay} 와
     * 동일. {@code reason} 누락 시 400.
     */
    @PostMapping("/bulk-discard")
    public DlqBulkResult bulkDiscard(
            @Valid @RequestBody BulkDiscardRequest body, HttpServletRequest request) {
        rateLimit(RL_SCOPE_BULK, request);
        return bulkUseCase.bulkDiscard(body.toFilter(), body.confirmedOrDefault(), body.reason());
    }

    /** bulk job 진행도 / 결과 조회. */
    @GetMapping("/bulk-jobs/{jobId}")
    public DlqBulkJob bulkJob(
            @PathVariable("jobId") UUID jobId, HttpServletRequest request) {
        rateLimit(RL_SCOPE_READ, request);
        return bulkUseCase.getBulkJob(jobId)
                .orElseThrow(() -> new AttemptNotFoundException(jobId));
    }

    /**
     * bulk-replay / bulk-discard 의 공통 request body.
     *
     * <p>{@code confirm} default = false → 항상 dry-run 부터. 운영자가 명시적으로 {@code true} 줘야
     * 실행됨 — "한 번 더 확인" 안전망.
     */
    public record BulkRequest(
            ChannelType channel,
            String topic,
            String consumerGroup,
            Instant from,
            Instant to,
            @Size(max = 256) String errorType,
            Boolean confirm,
            @Size(max = 256) String reason) {

        public DlqEntryFilter toFilter() {
            return new DlqEntryFilter(channel, topic, consumerGroup, from, to, errorType);
        }

        public boolean confirmedOrDefault() {
            return Boolean.TRUE.equals(confirm);
        }
    }

    /** discard 는 reason 필수 — 따로 record 로 NotBlank. */
    public record BulkDiscardRequest(
            ChannelType channel,
            String topic,
            String consumerGroup,
            Instant from,
            Instant to,
            @Size(max = 256) String errorType,
            Boolean confirm,
            @NotBlank @Size(max = 256) String reason) {

        public DlqEntryFilter toFilter() {
            return new DlqEntryFilter(channel, topic, consumerGroup, from, to, errorType);
        }

        public boolean confirmedOrDefault() {
            return Boolean.TRUE.equals(confirm);
        }
    }

    /**
     * audit 누락 / 잘못된 호출을 막기 위해 explicit DELETE 는 노출 X — discard 가 soft-delete 역할.
     * 운영자가 hard-delete 가 필요하면 DB 직접 작업 + audit 로 명시. 이 메서드는 자리만 잡고
     * 405 로 응답해 호출 시 명확한 안내.
     */
    @DeleteMapping("/{attemptId}")
    public void hardDeleteNotAllowed(
            @PathVariable("attemptId") UUID attemptId, HttpServletRequest request) {
        rateLimit(RL_SCOPE_WRITE, request);
        throw new UnsupportedOperationException(
                "hard delete not supported — use POST /discard for soft delete with reason");
    }

    /**
     * 호출자 IP + scope 기준 admin rate limit. 초과 시 429.
     *
     * <p>IP 추출은 X-Forwarded-For 우선 (LB 뒤). 첫번째 토큰 사용.
     */
    private void rateLimit(String scope, HttpServletRequest request) {
        String callerKey = clientKey(request);
        RateLimitDecision decision = adminRateLimiter.tryConsume(scope, callerKey);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(scope, decision.retryAfterMillis());
        }
    }

    private static String clientKey(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma < 0 ? xff : xff.substring(0, comma)).trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
