package com.example.notification.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.notification.adapter.in.exception.GlobalExceptionHandler;
import com.example.notification.adapter.in.security.AdminAuthFilter;
import com.example.notification.application.dto.DlqEntryView;
import com.example.notification.application.exception.UnauthorizedAdminException;
import com.example.notification.application.port.in.DlqAdminUseCase;
import com.example.notification.application.security.AdminContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminDlqControllerTest {

    private final DlqAdminUseCase useCase = org.mockito.Mockito.mock(DlqAdminUseCase.class);

    private MockMvc mvcWithToken(String configuredToken) {
        return MockMvcBuilders.standaloneSetup(new AdminDlqController(useCase))
                .addFilters(new AdminAuthFilter(configuredToken))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void 토큰_누락_시_401() throws Exception {
        // Filter 가 admin=false 세팅 → useCase 가 UnauthorizedAdminException 던지도록.
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

        // 빈 string 으로 token 설정 → 모든 admin 요청 거절
        mvcWithToken("").perform(get("/api/v1/admin/dlq").header("X-Admin-Token", "anything"))
                .andExpect(status().isUnauthorized());
    }
}
