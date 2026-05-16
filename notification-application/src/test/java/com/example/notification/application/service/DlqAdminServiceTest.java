package com.example.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.notification.application.dto.DlqEntryFilter;
import com.example.notification.application.exception.AttemptNotFoundException;
import com.example.notification.application.exception.IllegalDlqOperationException;
import com.example.notification.application.exception.UnauthorizedAdminException;
import com.example.notification.application.port.out.AuditLogger;
import com.example.notification.application.port.out.DeliveryAttemptRepository;
import com.example.notification.application.port.out.DlqStatRow;
import com.example.notification.application.port.out.OutboxPublisher;
import com.example.notification.application.security.AdminContext;
import com.example.notification.domain.channel.Channel;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.delivery.DeliveryAttempt;
import com.example.notification.domain.delivery.DeliveryRequested;
import com.example.notification.domain.delivery.DeliveryStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DlqAdminServiceTest {

    @Mock DeliveryAttemptRepository repository;
    @Mock OutboxPublisher outboxPublisher;
    @Mock AuditLogger auditLogger;

    DlqAdminService sut;

    @BeforeEach
    void setUp() {
        sut = new DlqAdminService(repository, outboxPublisher, auditLogger);
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    // ========== 기존 (ADR-0012) 회귀 ==========

    @Test
    void admin_아니면_list_거절() {
        AdminContext.set(false);
        assertThatThrownBy(() -> sut.list(null, 10))
                .isInstanceOf(UnauthorizedAdminException.class);
    }

    @Test
    void admin_이면_EXHAUSTED_조회() {
        AdminContext.set(true);
        DeliveryAttempt e = exhausted();
        when(repository.findByStatusAfter(eq(DeliveryStatus.EXHAUSTED), any(), eq(50)))
                .thenReturn(List.of(e));

        var result = sut.list(null, 50);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).attemptId()).isEqualTo(e.id());
        assertThat(result.get(0).status()).isEqualTo("EXHAUSTED");
    }

    @Test
    void replay_시_PENDING_복귀_및_outbox_재발행_및_audit() {
        AdminContext.set(true);
        DeliveryAttempt e = exhausted();
        when(repository.findById(e.id())).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var view = sut.replay(e.id());

        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(e.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(e.retryCount()).isZero();
        verify(outboxPublisher)
                .publish(
                        eq("notification.delivery." + e.channel().type().name().toLowerCase()),
                        eq(e.id().toString()),
                        any(DeliveryRequested.class));
        verify(auditLogger).log(eq("admin"), eq("DLQ_REPLAY"), any());
    }

    @Test
    void EXHAUSTED_가_아니면_replay_거절() {
        AdminContext.set(true);
        DeliveryAttempt pending = newPushAttempt();
        when(repository.findById(pending.id())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> sut.replay(pending.id()))
                .isInstanceOf(IllegalDlqOperationException.class);
        verify(outboxPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void discard_시_PERMANENTLY_FAILED() {
        AdminContext.set(true);
        DeliveryAttempt e = exhausted();
        when(repository.findById(e.id())).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var view = sut.discard(e.id(), "운영자 판단: 무의미");

        assertThat(view.status()).isEqualTo("PERMANENTLY_FAILED");
        assertThat(e.status()).isEqualTo(DeliveryStatus.PERMANENTLY_FAILED);
        assertThat(e.failureReason()).contains("discarded");
        verify(outboxPublisher, never()).publish(any(), any(), any()); // discard 는 발행 X
        verify(auditLogger).log(eq("admin"), eq("DLQ_DISCARD"), any());
    }

    @Test
    void 없는_attemptId_는_404() {
        AdminContext.set(true);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> sut.replay(id))
                .isInstanceOf(AttemptNotFoundException.class);
    }

    @Test
    void admin_아니면_replay_거절() {
        AdminContext.set(false);
        assertThatThrownBy(() -> sut.replay(UUID.randomUUID()))
                .isInstanceOf(UnauthorizedAdminException.class);
        verify(repository, never()).findById(any());
    }

    // ========== 확장 (ADR-0015) ==========

    @Test
    void search_는_filter_와_size_캡_적용() {
        AdminContext.set(true);
        DeliveryAttempt e = exhausted();
        when(repository.searchExhausted(eq(ChannelType.PUSH), any(), any(), any(), any(), eq(200)))
                .thenReturn(List.of(e));

        // size 9999 → 200 으로 캡
        var page =
                sut.search(
                        new DlqEntryFilter(ChannelType.PUSH, null, null, null, null, null),
                        null,
                        9999);

        assertThat(page.items()).hasSize(1);
        assertThat(page.size()).isEqualTo(200);
        // 결과가 size 미만이면 nextCursor null
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void search_는_topic_으로_channel_유도() {
        AdminContext.set(true);
        when(repository.searchExhausted(
                        eq(ChannelType.EMAIL), any(), any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of());

        sut.search(
                new DlqEntryFilter(null, "notification.delivery.email", null, null, null, null),
                null,
                10);

        verify(repository)
                .searchExhausted(eq(ChannelType.EMAIL), any(), any(), any(), any(), eq(10));
    }

    @Test
    void search_는_모르는_consumerGroup_이면_빈_페이지() {
        AdminContext.set(true);

        var page =
                sut.search(
                        new DlqEntryFilter(null, null, "unknown-group", null, null, null), null, 50);

        assertThat(page.items()).isEmpty();
        verify(repository, never())
                .searchExhausted(any(), any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void detail_없으면_Optional_empty() {
        AdminContext.set(true);
        when(repository.findById(any())).thenReturn(Optional.empty());

        assertThat(sut.detail(UUID.randomUUID())).isEmpty();
    }

    @Test
    void detail_있으면_full_payload() {
        AdminContext.set(true);
        DeliveryAttempt e = exhausted();
        when(repository.findById(e.id())).thenReturn(Optional.of(e));

        var detail = sut.detail(e.id()).orElseThrow();

        assertThat(detail.attemptId()).isEqualTo(e.id());
        assertThat(detail.expectedTopic()).isEqualTo("notification.delivery.push");
        assertThat(detail.maxRetry()).isEqualTo(DeliveryAttempt.MAX_RETRY);
        assertThat(detail.renderedBody()).isNotEmpty();
        // errorClass 는 "vendor" — failureReason "vendor down" 의 첫 token
        assertThat(detail.errorClass()).isEqualTo("vendor");
    }

    @Test
    void stats_기본_24h_1h_bucket() {
        AdminContext.set(true);
        Instant now = Instant.now();
        when(repository.aggregateExhaustedStats(any(), any(), eq(Duration.ofHours(1))))
                .thenReturn(
                        List.of(
                                new DlqStatRow(now, ChannelType.PUSH, "vendor", 3L),
                                new DlqStatRow(now, ChannelType.EMAIL, "timeout", 1L)));

        var stats = sut.stats(null, null, null);

        assertThat(stats.totalCount()).isEqualTo(4L);
        assertThat(stats.byChannel())
                .extracting(c -> c.key())
                .containsExactlyInAnyOrder("PUSH", "EMAIL");
        assertThat(stats.byErrorClass())
                .extracting(c -> c.key())
                .containsExactlyInAnyOrder("vendor", "timeout");
        assertThat(stats.bucketDuration()).isEqualTo(Duration.ofHours(1));
    }

    // ========== helpers ==========

    private DeliveryAttempt exhausted() {
        DeliveryAttempt a = newPushAttempt();
        for (int i = 0; i < DeliveryAttempt.MAX_RETRY; i++) {
            a.markDispatching();
            a.markFailed("vendor down");
        }
        return a;
    }

    private static DeliveryAttempt newPushAttempt() {
        return DeliveryAttempt.create(
                UUID.randomUUID(),
                new Channel(ChannelType.PUSH, "p".repeat(160)),
                "title",
                "body");
    }
}
