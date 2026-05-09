package com.example.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.notification.application.exception.AttemptNotFoundException;
import com.example.notification.application.exception.IllegalDlqOperationException;
import com.example.notification.application.exception.UnauthorizedAdminException;
import com.example.notification.application.port.out.AuditLogger;
import com.example.notification.application.port.out.DeliveryAttemptRepository;
import com.example.notification.application.port.out.OutboxPublisher;
import com.example.notification.application.security.AdminContext;
import com.example.notification.domain.channel.Channel;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.delivery.DeliveryAttempt;
import com.example.notification.domain.delivery.DeliveryRequested;
import com.example.notification.domain.delivery.DeliveryStatus;
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
