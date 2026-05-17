package com.example.notification.adapter.`in`.rest

import com.example.notification.application.dto.DlqBulkJob
import com.example.notification.application.dto.DlqBulkResult
import com.example.notification.application.dto.DlqEntryDetail
import com.example.notification.application.dto.DlqEntryFilter
import com.example.notification.application.dto.DlqEntryView
import com.example.notification.application.dto.DlqListPage
import com.example.notification.application.dto.DlqStats
import com.example.notification.application.exception.AttemptNotFoundException
import com.example.notification.application.exception.RateLimitExceededException
import com.example.notification.application.port.`in`.DlqAdminUseCase
import com.example.notification.application.port.`in`.DlqBulkAdminUseCase
import com.example.notification.application.port.out.AdminRateLimiter
import com.example.notification.domain.channel.ChannelType
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * DLQ (EXHAUSTED) 운영 endpoint. [com.example.notification.adapter.in.security.AdminAuthFilter] 가
 * 요청별로 admin role 검증 → 통과하면 use case 가 실행. 비-admin 호출은 use case 단에서 401.
 *
 * 모든 endpoint 는 호출자 IP 별 admin rate limit (분당 60). 초과 시 429 + Retry-After.
 *
 * ADR-0012 (초기 단건 API) + ADR-0015 (filter / bulk / stats 확장) 참조. 기존 endpoint 는
 * 호환을 위해 그대로 유지.
 */
@RestController
@RequestMapping("/api/v1/admin/dlq")
class AdminDlqController(
    private val useCase: DlqAdminUseCase,
    private val bulkUseCase: DlqBulkAdminUseCase,
    private val adminRateLimiter: AdminRateLimiter,
) {

    // ============================================================
    // 기존 (ADR-0012). 호환 유지.
    // ============================================================

    /**
     * EXHAUSTED 항목 cursor 페이지네이션. limit 1~200.
     *
     * 호환 endpoint — `?topic=&channel=&...` 같은 확장 필터는 [search] (별 path `/search`) 에서 받음.
     */
    @GetMapping
    fun list(
        @RequestParam(value = "cursor", required = false) cursor: UUID?,
        @RequestParam(value = "limit", defaultValue = "50") limit: Int,
        request: HttpServletRequest,
    ): List<DlqEntryView> {
        rateLimit(RL_SCOPE_READ, request)
        return useCase.list(cursor, limit)
    }

    /** 한 항목 재발송 — retry=0 으로 환원 + Outbox 재발행. */
    @PostMapping("/{attemptId}/replay")
    fun replay(
        @PathVariable("attemptId") attemptId: UUID,
        request: HttpServletRequest,
    ): DlqEntryView {
        rateLimit(RL_SCOPE_WRITE, request)
        return useCase.replay(attemptId)
    }

    /** 한 항목 영구 종료 — PERMANENTLY_FAILED 로 마킹. body 의 reason 는 audit 에 기록. */
    @PostMapping("/{attemptId}/discard")
    fun discard(
        @PathVariable("attemptId") attemptId: UUID,
        @RequestBody(required = false) body: DiscardRequest?,
        request: HttpServletRequest,
    ): DlqEntryView {
        rateLimit(RL_SCOPE_WRITE, request)
        val reason = body?.reason
        return useCase.discard(attemptId, reason)
    }

    @JvmRecord
    data class DiscardRequest(@field:Size(max = 256) val reason: String?)

    // ============================================================
    // 확장 (ADR-0015).
    // ============================================================

    /**
     * filter 조건으로 검색 (cursor 페이지네이션).
     *
     * `GET /api/v1/admin/dlq/search?channel=PUSH&topic=&consumerGroup=&from=&to=&errorType=&cursor=&size=`.
     *
     * `channel` 과 `topic` 모두 주면 `channel` 우선. `from`/`to` 는 ISO-8601 instant
     * (예: `2026-05-15T00:00:00Z`). `size` 는 1~200 캡.
     */
    @GetMapping("/search")
    fun search(
        @RequestParam(value = "channel", required = false) channel: ChannelType?,
        @RequestParam(value = "topic", required = false) topic: String?,
        @RequestParam(value = "consumerGroup", required = false) consumerGroup: String?,
        @RequestParam(value = "from", required = false) from: Instant?,
        @RequestParam(value = "to", required = false) to: Instant?,
        @RequestParam(value = "errorType", required = false) errorType: String?,
        @RequestParam(value = "cursor", required = false) cursor: UUID?,
        @RequestParam(value = "size", defaultValue = "50") size: Int,
        request: HttpServletRequest,
    ): DlqListPage {
        rateLimit(RL_SCOPE_READ, request)
        val filter = DlqEntryFilter(channel, topic, consumerGroup, from, to, errorType)
        return useCase.search(filter, cursor, size)
    }

    /** 단건 detail — payload 전체 + retry context. 없으면 404. */
    @GetMapping("/{attemptId}")
    fun detail(
        @PathVariable("attemptId") attemptId: UUID,
        request: HttpServletRequest,
    ): DlqEntryDetail {
        rateLimit(RL_SCOPE_READ, request)
        return useCase.detail(attemptId)
            .orElseThrow { AttemptNotFoundException(attemptId) }
    }

    /**
     * 시간 bucket 별 stats. `GET /api/v1/admin/dlq/stats?from=&to=&bucket=PT1H`. bucket 은
     * ISO-8601 duration (예: `PT1H`, `PT15M`). 기본 1시간, 범위 기본 최근 24h.
     */
    @GetMapping("/stats")
    fun stats(
        @RequestParam(value = "from", required = false) from: Instant?,
        @RequestParam(value = "to", required = false) to: Instant?,
        @RequestParam(value = "bucket", required = false) bucket: Duration?,
        request: HttpServletRequest,
    ): DlqStats {
        rateLimit(RL_SCOPE_READ, request)
        return useCase.stats(from, to, bucket)
    }

    /**
     * bulk replay. `confirm=true` 일 때만 실행, 그 외에는 dry-run (대상 개수 + sample id).
     * 이는 안전 가드 — confirm 없는 호출이 곧바로 수천 건을 재발송하지 못하게 한다.
     *
     * 응답: dry-run 이면 `mode=DRY_RUN`, 실 실행이면 `mode=EXECUTING + jobId`.
     */
    @PostMapping("/bulk-replay")
    fun bulkReplay(
        @Valid @RequestBody body: BulkRequest,
        request: HttpServletRequest,
    ): DlqBulkResult {
        rateLimit(RL_SCOPE_BULK, request)
        return bulkUseCase.bulkReplay(body.toFilter(), body.confirmedOrDefault(), body.reason)
    }

    /**
     * bulk discard — reason 필수 (body 의 `reason`). dry-run 의미는 [bulkReplay] 와 동일.
     * `reason` 누락 시 400.
     */
    @PostMapping("/bulk-discard")
    fun bulkDiscard(
        @Valid @RequestBody body: BulkDiscardRequest,
        request: HttpServletRequest,
    ): DlqBulkResult {
        rateLimit(RL_SCOPE_BULK, request)
        return bulkUseCase.bulkDiscard(body.toFilter(), body.confirmedOrDefault(), body.reason)
    }

    /** bulk job 진행도 / 결과 조회. */
    @GetMapping("/bulk-jobs/{jobId}")
    fun bulkJob(
        @PathVariable("jobId") jobId: UUID,
        request: HttpServletRequest,
    ): DlqBulkJob {
        rateLimit(RL_SCOPE_READ, request)
        return bulkUseCase.getBulkJob(jobId)
            .orElseThrow { AttemptNotFoundException(jobId) }
    }

    /**
     * bulk-replay / bulk-discard 의 공통 request body.
     *
     * `confirm` default = false → 항상 dry-run 부터. 운영자가 명시적으로 `true` 줘야
     * 실행됨 — "한 번 더 확인" 안전망.
     */
    @JvmRecord
    data class BulkRequest(
        val channel: ChannelType?,
        val topic: String?,
        val consumerGroup: String?,
        val from: Instant?,
        val to: Instant?,
        @field:Size(max = 256) val errorType: String?,
        val confirm: Boolean?,
        @field:Size(max = 256) val reason: String?,
    ) {

        fun toFilter(): DlqEntryFilter =
            DlqEntryFilter(channel, topic, consumerGroup, from, to, errorType)

        fun confirmedOrDefault(): Boolean = confirm == true
    }

    /** discard 는 reason 필수 — 따로 record 로 NotBlank. */
    @JvmRecord
    data class BulkDiscardRequest(
        val channel: ChannelType?,
        val topic: String?,
        val consumerGroup: String?,
        val from: Instant?,
        val to: Instant?,
        @field:Size(max = 256) val errorType: String?,
        val confirm: Boolean?,
        @field:NotBlank @field:Size(max = 256) val reason: String,
    ) {

        fun toFilter(): DlqEntryFilter =
            DlqEntryFilter(channel, topic, consumerGroup, from, to, errorType)

        fun confirmedOrDefault(): Boolean = confirm == true
    }

    /**
     * audit 누락 / 잘못된 호출을 막기 위해 explicit DELETE 는 노출 X — discard 가 soft-delete 역할.
     * 운영자가 hard-delete 가 필요하면 DB 직접 작업 + audit 로 명시. 이 메서드는 자리만 잡고
     * 405 로 응답해 호출 시 명확한 안내.
     */
    @DeleteMapping("/{attemptId}")
    fun hardDeleteNotAllowed(
        @PathVariable("attemptId") attemptId: UUID,
        request: HttpServletRequest,
    ) {
        rateLimit(RL_SCOPE_WRITE, request)
        throw UnsupportedOperationException(
            "hard delete not supported — use POST /discard for soft delete with reason",
        )
    }

    /**
     * 호출자 IP + scope 기준 admin rate limit. 초과 시 429.
     *
     * IP 추출은 X-Forwarded-For 우선 (LB 뒤). 첫번째 토큰 사용.
     */
    private fun rateLimit(scope: String, request: HttpServletRequest) {
        val callerKey = clientKey(request)
        val decision = adminRateLimiter.tryConsume(scope, callerKey)
        if (!decision.allowed) {
            throw RateLimitExceededException(scope, decision.retryAfterMillis)
        }
    }

    companion object {
        const val RL_SCOPE_READ = "dlq.read"
        const val RL_SCOPE_WRITE = "dlq.write"
        const val RL_SCOPE_BULK = "dlq.bulk"

        private fun clientKey(request: HttpServletRequest): String {
            val xff = request.getHeader("X-Forwarded-For")
            if (xff != null && xff.isNotBlank()) {
                val comma = xff.indexOf(',')
                return (if (comma < 0) xff else xff.substring(0, comma)).trim()
            }
            return request.remoteAddr ?: "unknown"
        }
    }
}
