package com.example.notification.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.notification.adapter.in.exception.GlobalExceptionHandler;
import com.example.notification.adapter.in.security.AdminAuthFilter;
import com.example.notification.application.dto.DlqBulkJob;
import com.example.notification.application.dto.DlqBulkResult;
import com.example.notification.application.dto.DlqEntryDetail;
import com.example.notification.application.dto.DlqEntryFilter;
import com.example.notification.application.dto.DlqEntryView;
import com.example.notification.application.dto.DlqListPage;
import com.example.notification.application.dto.DlqStats;
import com.example.notification.application.exception.UnauthorizedAdminException;
import com.example.notification.application.port.in.DlqAdminUseCase;
import com.example.notification.application.port.in.DlqBulkAdminUseCase;
import com.example.notification.application.port.out.AdminRateLimiter;
import com.example.notification.application.security.AdminContext;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.shared.RateLimitDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminDlqControllerTest {

    private final DlqAdminUseCase useCase = org.mockito.Mockito.mock(DlqAdminUseCase.class);
    private final DlqBulkAdminUseCase bulkUseCase =
            org.mockito.Mockito.mock(DlqBulkAdminUseCase.class);
    private final AdminRateLimiter rateLimiter = org.mockito.Mockito.mock(AdminRateLimiter.class);
    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    private MockMvc mvcWithToken(String configuredToken) {
        // jackson + jsr310 — Instant 직렬화에 필요. 기본 MappingJackson2HttpMessageConverter 는
        // JavaTimeModule 등록 안 되어 있어 Instant 가 epoch ms 로 나가는 문제 회피.
        MappingJackson2HttpMessageConverter jacksonConverter =
                new MappingJackson2HttpMessageConverter(objectMapper);
        return MockMvcBuilders.standaloneSetup(
                        new AdminDlqController(useCase, bulkUseCase, rateLimiter))
                .addFilters(new AdminAuthFilter(configuredToken))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(jacksonConverter)
                .build();
    }

    @BeforeEach
    void allowRateLimit() {
        // 기본은 통과. 거절 케이스만 별도로 stub 변경.
        when(rateLimiter.tryConsume(any(), any())).thenReturn(RateLimitDecision.allow(100));
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    // ========== 기존 (ADR-0012) ==========

    @Test
    void 토큰_누락_시_401() throws Exception {
        when(useCase.list(any(), any(Integer.class)))
                .thenThrow(new UnauthorizedAdminException("admin role required"));

        mvcWithToken("secret-1234")
                .perform(get("/api/v1/admin/dlq"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 토큰_불일치_시_401() throws Exception {
        when(useCase.list(any(), any(Integer.class)))
                .thenThrow(new UnauthorizedAdminException("admin role required"));

        mvcWithToken("secret-1234")
                .perform(get("/api/v1/admin/dlq").header("X-Admin-Token", "WRONG"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 정상_토큰_이면_200_및_use_case_호출() throws Exception {
        UUID id = UUID.randomUUID();
        when(useCase.list(any(), eq(50)))
                .thenReturn(
                        List.of(
                                new DlqEntryView(
                                        id,
                                        UUID.randomUUID(),
                                        "PUSH",
                                        "PUSH:p***p",
                                        "EXHAUSTED",
                                        5,
                                        Instant.now(),
                                        Instant.now(),
                                        "vendor down",
                                        100)));

        mvcWithToken("secret-1234")
                .perform(get("/api/v1/admin/dlq").header("X-Admin-Token", "secret-1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attemptId").value(id.toString()))
                .andExpect(jsonPath("$[0].status").value("EXHAUSTED"));
        verify(useCase).list(any(), eq(50));
    }

    @Test
    void replay_endpoint_호출() throws Exception {
        UUID id = UUID.randomUUID();
        when(useCase.replay(id))
                .thenReturn(
                        new DlqEntryView(
                                id,
                                UUID.randomUUID(),
                                "PUSH",
                                "PUSH:p***p",
                                "PENDING",
                                0,
                                Instant.now(),
                                null,
                                null,
                                100));

        mvcWithToken("secret-1234")
                .perform(
                        post("/api/v1/admin/dlq/" + id + "/replay")
                                .header("X-Admin-Token", "secret-1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        verify(useCase).replay(id);
    }

    @Test
    void discard_endpoint_호출() throws Exception {
        UUID id = UUID.randomUUID();
        when(useCase.discard(eq(id), eq("무의미")))
                .thenReturn(
                        new DlqEntryView(
                                id,
                                UUID.randomUUID(),
                                "PUSH",
                                "PUSH:p***p",
                                "PERMANENTLY_FAILED",
                                5,
                                Instant.now(),
                                Instant.now(),
                                "discarded: 무의미",
                                100));

        mvcWithToken("secret-1234")
                .perform(
                        post("/api/v1/admin/dlq/" + id + "/discard")
                                .header("X-Admin-Token", "secret-1234")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"무의미\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PERMANENTLY_FAILED"));
        verify(useCase).discard(id, "무의미");
    }

    @Test
    void admin_yml_token_미설정_이면_default_deny() throws Exception {
        when(useCase.list(any(), any(Integer.class)))
                .thenThrow(new UnauthorizedAdminException("admin role required"));

        mvcWithToken("").perform(get("/api/v1/admin/dlq").header("X-Admin-Token", "anything"))
                .andExpect(status().isUnauthorized());
    }

    // ========== 확장 (ADR-0015) ==========

    @Test
    void search_endpoint_filter_파라미터_전달() throws Exception {
        when(useCase.search(any(), any(), any(Integer.class)))
                .thenReturn(new DlqListPage(List.of(), null, 50));

        mvcWithToken("secret-1234")
                .perform(
                        get("/api/v1/admin/dlq/search")
                                .header("X-Admin-Token", "secret-1234")
                                .param("channel", "PUSH")
                                .param("from", "2026-05-15T00:00:00Z")
                                .param("to", "2026-05-16T00:00:00Z")
                                .param("errorType", "vendor")
                                .param("size", "100"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<DlqEntryFilter> captor =
                org.mockito.ArgumentCaptor.forClass(DlqEntryFilter.class);
        verify(useCase).search(captor.capture(), eq(null), eq(100));
        DlqEntryFilter f = captor.getValue();
        assert f.channelType() == ChannelType.PUSH;
        assert "vendor".equals(f.errorContains());
        assert f.from().equals(Instant.parse("2026-05-15T00:00:00Z"));
    }

    @Test
    void detail_endpoint_없으면_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(useCase.detail(id)).thenReturn(Optional.empty());

        mvcWithToken("secret-1234")
                .perform(
                        get("/api/v1/admin/dlq/" + id)
                                .header("X-Admin-Token", "secret-1234"))
                .andExpect(status().isNotFound());
    }

    @Test
    void detail_endpoint_있으면_200() throws Exception {
        UUID id = UUID.randomUUID();
        when(useCase.detail(id))
                .thenReturn(
                        Optional.of(
                                new DlqEntryDetail(
                                        id,
                                        UUID.randomUUID(),
                                        "PUSH",
                                        "PUSH:p***p",
                                        "EXHAUSTED",
                                        5,
                                        5,
                                        Instant.now(),
                                        Instant.now(),
                                        null,
                                        "vendor-msg-1",
                                        "VendorTransientException: down",
                                        "VendorTransientException",
                                        "title",
                                        "body",
                                        "notification.delivery.push")));

        mvcWithToken("secret-1234")
                .perform(
                        get("/api/v1/admin/dlq/" + id)
                                .header("X-Admin-Token", "secret-1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(id.toString()))
                .andExpect(jsonPath("$.errorClass").value("VendorTransientException"))
                .andExpect(jsonPath("$.expectedTopic").value("notification.delivery.push"));
    }

    @Test
    void stats_endpoint_bucket_기본() throws Exception {
        when(useCase.stats(any(), any(), any()))
                .thenReturn(
                        new DlqStats(
                                Instant.parse("2026-05-15T00:00:00Z"),
                                Instant.parse("2026-05-16T00:00:00Z"),
                                Duration.ofHours(1),
                                10L,
                                List.of(),
                                List.of(),
                                List.of()));

        mvcWithToken("secret-1234")
                .perform(
                        get("/api/v1/admin/dlq/stats")
                                .header("X-Admin-Token", "secret-1234")
                                .param("bucket", "PT1H"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(10));

        verify(useCase).stats(any(), any(), eq(Duration.ofHours(1)));
    }

    @Test
    void bulk_replay_dry_run_default() throws Exception {
        when(bulkUseCase.bulkReplay(any(), eq(false), any()))
                .thenReturn(DlqBulkResult.dryRun(5L, List.of(UUID.randomUUID())));

        mvcWithToken("secret-1234")
                .perform(
                        post("/api/v1/admin/dlq/bulk-replay")
                                .header("X-Admin-Token", "secret-1234")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"channel\":\"PUSH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DRY_RUN"))
                .andExpect(jsonPath("$.estimatedCount").value(5))
                .andExpect(jsonPath("$.jobId").isEmpty());
    }

    @Test
    void bulk_replay_confirm_true_executing() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(bulkUseCase.bulkReplay(any(), eq(true), any()))
                .thenReturn(DlqBulkResult.executing(jobId, 3L, List.of()));

        mvcWithToken("secret-1234")
                .perform(
                        post("/api/v1/admin/dlq/bulk-replay")
                                .header("X-Admin-Token", "secret-1234")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"channel\":\"PUSH\",\"confirm\":true,"
                                                + "\"reason\":\"incident rebound\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("EXECUTING"))
                .andExpect(jsonPath("$.jobId").value(jobId.toString()));
    }

    @Test
    void bulk_discard_reason_blank_이면_400() throws Exception {
        mvcWithToken("secret-1234")
                .perform(
                        post("/api/v1/admin/dlq/bulk-discard")
                                .header("X-Admin-Token", "secret-1234")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirm\":true,\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulk_job_조회() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(bulkUseCase.getBulkJob(jobId))
                .thenReturn(
                        Optional.of(
                                new DlqBulkJob(
                                        jobId,
                                        DlqBulkJob.Operation.REPLAY,
                                        DlqBulkJob.State.SUCCEEDED,
                                        5L, 5L, 5L, 0L,
                                        Instant.now(),
                                        Instant.now(),
                                        null)));

        mvcWithToken("secret-1234")
                .perform(
                        get("/api/v1/admin/dlq/bulk-jobs/" + jobId)
                                .header("X-Admin-Token", "secret-1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.successCount").value(5));
    }

    @Test
    void hard_delete_는_405_상응_500_X_500_X_허용_안_됨_안내() throws Exception {
        mvcWithToken("secret-1234")
                .perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/v1/admin/dlq/" + UUID.randomUUID())
                                .header("X-Admin-Token", "secret-1234"))
                .andExpect(status().isInternalServerError())
                // GlobalExceptionHandler 의 unexpected handler — message 에 hard delete not supported
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("hard delete not supported")));
    }

    @Test
    void rate_limit_초과_시_429_및_Retry_After_헤더() throws Exception {
        when(rateLimiter.tryConsume(any(), any()))
                .thenReturn(RateLimitDecision.deny(5_000L));

        mvcWithToken("secret-1234")
                .perform(
                        get("/api/v1/admin/dlq")
                                .header("X-Admin-Token", "secret-1234"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "5"));
    }
}
