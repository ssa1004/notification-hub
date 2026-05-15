package com.example.notification.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.notification.adapter.in.exception.GlobalExceptionHandler;
import com.example.notification.application.dto.DeliveryHistoryPage;
import com.example.notification.application.dto.SendNotificationResult;
import com.example.notification.application.exception.DuplicateRequestException;
import com.example.notification.application.exception.RateLimitExceededException;
import com.example.notification.application.port.in.ListMyDeliveriesUseCase;
import com.example.notification.application.port.in.SendNotificationUseCase;
import com.example.notification.domain.notification.NotificationStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificationControllerTest {

    private final SendNotificationUseCase sendUseCase = org.mockito.Mockito.mock(SendNotificationUseCase.class);
    private final ListMyDeliveriesUseCase listUseCase = org.mockito.Mockito.mock(ListMyDeliveriesUseCase.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new NotificationController(sendUseCase, listUseCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private static final String VALID_BODY =
            "{\"recipientId\":\"u-1\",\"kind\":\"SECURITY\",\"title\":\"OTP\",\"body\":\"코드: 1\"}";

    @Test
    void send_returns_202_when_fanned_out() throws Exception {
        when(sendUseCase.send(any())).thenReturn(
                new SendNotificationResult(
                        UUID.randomUUID(),
                        NotificationStatus.FANNED_OUT,
                        List.of(),
                        null));

        mvc.perform(post("/api/v1/notifications")
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isAccepted());
    }

    @Test
    void send_returns_200_when_suppressed() throws Exception {
        when(sendUseCase.send(any())).thenReturn(
                new SendNotificationResult(
                        UUID.randomUUID(),
                        NotificationStatus.SUPPRESSED,
                        List.of(),
                        "OPT_OUT"));

        mvc.perform(post("/api/v1/notifications")
                        .header("Idempotency-Key", "k2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void duplicate_request_returns_409() throws Exception {
        when(sendUseCase.send(any())).thenThrow(new DuplicateRequestException("k3"));

        mvc.perform(post("/api/v1/notifications")
                        .header("Idempotency-Key", "k3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void rate_limit_returns_429_with_retry_after_header() throws Exception {
        when(sendUseCase.send(any()))
                .thenThrow(new RateLimitExceededException("PUSH", 30_000));

        mvc.perform(post("/api/v1/notifications")
                        .header("Idempotency-Key", "k4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"));
    }

    @Test
    void me_with_garbage_cursor_returns_400_not_500() throws Exception {
        // UUID 자리에 UUID 가 아닌 값 → MethodArgumentTypeMismatchException.
        // 호출자 입력 오류이므로 400 이어야 하고, use case 까지 닿으면 안 된다.
        mvc.perform(get("/api/v1/notifications/me")
                        .param("recipientId", "u-1")
                        .param("cursor", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(listUseCase, never()).list(any(), any(), anyInt());
    }

    @Test
    void me_with_valid_cursor_reaches_use_case() throws Exception {
        UUID cursor = UUID.randomUUID();
        when(listUseCase.list(any(), any(), anyInt()))
                .thenReturn(new DeliveryHistoryPage(List.of(), null));

        mvc.perform(get("/api/v1/notifications/me")
                        .param("recipientId", "u-1")
                        .param("cursor", cursor.toString()))
                .andExpect(status().isOk());

        verify(listUseCase).list(eq("u-1"), eq(cursor), eq(20));
    }
}
