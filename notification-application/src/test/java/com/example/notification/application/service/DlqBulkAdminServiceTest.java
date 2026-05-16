package com.example.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.notification.application.dto.DlqBulkJob;
import com.example.notification.application.dto.DlqBulkResult;
import com.example.notification.application.dto.DlqEntryFilter;
import com.example.notification.application.exception.UnauthorizedAdminException;
import com.example.notification.application.port.out.AuditLogger;
import com.example.notification.application.port.out.DeliveryAttemptRepository;
import com.example.notification.application.port.out.DlqBulkJobRepository;
import com.example.notification.application.port.out.OutboxPublisher;
import com.example.notification.application.security.AdminContext;
import com.example.notification.domain.channel.Channel;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.delivery.DeliveryAttempt;
import com.example.notification.domain.delivery.DeliveryRequested;
import com.example.notification.domain.delivery.DeliveryStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class DlqBulkAdminServiceTest {

    @Mock DeliveryAttemptRepository repository;
    @Mock OutboxPublisher outboxPublisher;
    @Mock AuditLogger auditLogger;

    /** in-memory 단순 구현 — Mockito 로 stub 하기보다 동작 자체를 검증. */
    private final Map<UUID, DlqBulkJob> jobs = new HashMap<>();
    private final DlqBulkJobRepository bulkJobRepository =
            new DlqBulkJobRepository() {
                @Override
                public void create(DlqBulkJob job) {
                    jobs.put(job.jobId(), job);
                }

                @Override
                public void update(DlqBulkJob job) {
                    jobs.put(job.jobId(), job);
                }

                @Override
                public Optional<DlqBulkJob> findById(UUID jobId) {
                    return Optional.ofNullable(jobs.get(jobId));
                }
            };

    /** 동기 executor — 비동기 worker 의 결과를 단위 테스트에서 결정적으로 검증. */
    private final Executor sameThreadExecutor = Runnable::run;

    /** 항상 callback 실행 — 실제 트랜잭션 없이 in-memory 테스트. */
    private final TransactionTemplate txTemplate =
            new TransactionTemplate(mock(PlatformTransactionManager.class)) {
                @Override
                public <T> T execute(TransactionCallback<T> action) {
                    TransactionStatus status = new SimpleTransactionStatus();
                    return action.doInTransaction(status);
                }
            };

    DlqBulkAdminService sut;

    @BeforeEach
    void setUp() {
        DlqAdminService single = new DlqAdminService(repository, outboxPublisher, auditLogger);
        sut =
                new DlqBulkAdminService(
                        repository,
                        auditLogger,
                        bulkJobRepository,
                        single,
                        sameThreadExecutor,
                        txTemplate);
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
        jobs.clear();
    }

    @Test
    void bulkReplay_non_admin_거절() {
        AdminContext.set(false);
        assertThatThrownBy(() -> sut.bulkReplay(DlqEntryFilter.EMPTY, true, "x"))
                .isInstanceOf(UnauthorizedAdminException.class);
        verify(repository, never()).countExhausted(any(), any(), any(), any());
    }

    @Test
    void bulkReplay_default_는_dry_run() {
        AdminContext.set(true);
        DeliveryAttempt e = exhausted();
        when(repository.countExhausted(any(), any(), any(), any())).thenReturn(7L);
        when(repository.searchExhausted(any(), any(), any(), any(), any(), eq(10)))
                .thenReturn(List.of(e));

        var result = sut.bulkReplay(DlqEntryFilter.EMPTY, false, null);

        assertThat(result.mode()).isEqualTo(DlqBulkResult.Mode.DRY_RUN);
        assertThat(result.estimatedCount()).isEqualTo(7L);
        assertThat(result.sampleAttemptIds()).containsExactly(e.id());
        assertThat(result.jobId()).isNull();
        // dry-run 에선 audit 만 남고 outbox / save 호출 X
        verify(auditLogger).log(eq("admin"), eq("DLQ_BULK_REPLAY_DRYRUN"), any());
        verify(outboxPublisher, never()).publish(any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void bulkReplay_confirm_이면_job_생성_및_각_항목_replay() {
        AdminContext.set(true);
        DeliveryAttempt e1 = exhausted();
        DeliveryAttempt e2 = exhausted();
        when(repository.countExhausted(any(), any(), any(), any())).thenReturn(2L);
        // sample 조회 (10) + worker batch 조회 (100)
        when(repository.searchExhausted(any(), any(), any(), any(), any(), eq(10)))
                .thenReturn(List.of(e1, e2));
        when(repository.searchExhausted(any(), any(), any(), any(), any(), eq(100)))
                .thenReturn(List.of(e1, e2));
        lenient().when(repository.findById(e1.id())).thenReturn(Optional.of(e1));
        lenient().when(repository.findById(e2.id())).thenReturn(Optional.of(e2));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = sut.bulkReplay(DlqEntryFilter.EMPTY, true, "incident rebound");

        assertThat(result.mode()).isEqualTo(DlqBulkResult.Mode.EXECUTING);
        assertThat(result.jobId()).isNotNull();
        // 동기 executor 이므로 즉시 완료
        var job = bulkJobRepository.findById(result.jobId()).orElseThrow();
        assertThat(job.state()).isEqualTo(DlqBulkJob.State.SUCCEEDED);
        assertThat(job.successCount()).isEqualTo(2L);
        verify(outboxPublisher, org.mockito.Mockito.times(2))
                .publish(any(), any(), any(DeliveryRequested.class));
    }

    @Test
    void bulkDiscard_reason_blank_이면_400() {
        AdminContext.set(true);
        assertThatThrownBy(() -> sut.bulkDiscard(DlqEntryFilter.EMPTY, false, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bulkDiscard_confirm_각_항목_discard() {
        AdminContext.set(true);
        DeliveryAttempt e = exhausted();
        when(repository.countExhausted(any(), any(), any(), any())).thenReturn(1L);
        when(repository.searchExhausted(any(), any(), any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(e));
        when(repository.findById(e.id())).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = sut.bulkDiscard(DlqEntryFilter.EMPTY, true, "obsolete OTPs");

        assertThat(result.mode()).isEqualTo(DlqBulkResult.Mode.EXECUTING);
        var job = bulkJobRepository.findById(result.jobId()).orElseThrow();
        assertThat(job.state()).isEqualTo(DlqBulkJob.State.SUCCEEDED);
        assertThat(e.status()).isEqualTo(DeliveryStatus.PERMANENTLY_FAILED);
        verify(auditLogger).log(eq("admin"), eq("DLQ_BULK_DISCARD_START"), any());
        verify(auditLogger).log(eq("admin"), eq("DLQ_BULK_DISCARD_FINISH"), any());
    }

    @Test
    void getBulkJob_없으면_empty() {
        AdminContext.set(true);
        assertThat(sut.getBulkJob(UUID.randomUUID())).isEmpty();
    }

    @Test
    void getBulkJob_non_admin_거절() {
        AdminContext.set(false);
        assertThatThrownBy(() -> sut.getBulkJob(UUID.randomUUID()))
                .isInstanceOf(UnauthorizedAdminException.class);
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
