package com.example.notification.adapter.out.persistence;

import com.example.notification.adapter.out.persistence.entity.DeliveryAttemptEntity;
import com.example.notification.adapter.out.persistence.mapper.DeliveryAttemptMapper;
import com.example.notification.adapter.out.persistence.repository.DeliveryAttemptJpaRepository;
import com.example.notification.application.dto.DlqErrorClass;
import com.example.notification.application.port.out.DeliveryAttemptRepository;
import com.example.notification.application.port.out.DlqStatRow;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.delivery.DeliveryAttempt;
import com.example.notification.domain.delivery.DeliveryStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaDeliveryAttemptRepository implements DeliveryAttemptRepository {

    private static final UUID CURSOR_MIN = new UUID(0L, 0L);

    private final DeliveryAttemptJpaRepository jpa;

    @Override
    public DeliveryAttempt save(DeliveryAttempt attempt) {
        return DeliveryAttemptMapper.toDomain(jpa.save(DeliveryAttemptMapper.toEntity(attempt)));
    }

    @Override
    public List<DeliveryAttempt> saveAll(List<DeliveryAttempt> attempts) {
        return jpa.saveAll(attempts.stream().map(DeliveryAttemptMapper::toEntity).toList())
                .stream()
                .map(DeliveryAttemptMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<DeliveryAttempt> findById(UUID id) {
        return jpa.findById(id).map(DeliveryAttemptMapper::toDomain);
    }

    @Override
    public List<DeliveryAttempt> findByNotificationId(UUID notificationId) {
        return jpa.findByNotificationId(notificationId).stream()
                .map(DeliveryAttemptMapper::toDomain)
                .toList();
    }

    @Override
    public List<DeliveryAttempt> findByStatusAfter(DeliveryStatus status, UUID cursor, int limit) {
        UUID cursorEffective = cursor == null ? CURSOR_MIN : cursor;
        return jpa.findByStatusAndIdGreaterThanOrderByIdAsc(
                        status, cursorEffective, PageRequest.of(0, limit))
                .stream()
                .map(DeliveryAttemptMapper::toDomain)
                .toList();
    }

    @Override
    public List<DeliveryAttempt> searchExhausted(
            ChannelType channelType,
            Instant from,
            Instant to,
            String errorContains,
            UUID cursor,
            int limit) {
        UUID cursorEffective = cursor == null ? CURSOR_MIN : cursor;
        String pattern = errorContains == null || errorContains.isBlank()
                ? null
                : "%" + errorContains + "%";
        return jpa.searchExhausted(
                        DeliveryStatus.EXHAUSTED,
                        cursorEffective,
                        channelType,
                        from,
                        to,
                        pattern,
                        PageRequest.of(0, limit))
                .stream()
                .map(DeliveryAttemptMapper::toDomain)
                .toList();
    }

    @Override
    public long countExhausted(
            ChannelType channelType, Instant from, Instant to, String errorContains) {
        String pattern = errorContains == null || errorContains.isBlank()
                ? null
                : "%" + errorContains + "%";
        return jpa.countExhausted(DeliveryStatus.EXHAUSTED, channelType, from, to, pattern);
    }

    @Override
    public List<DlqStatRow> aggregateExhaustedStats(
            Instant from, Instant to, Duration bucketDuration) {
        long bucketMs = bucketDuration.toMillis();
        if (bucketMs <= 0) {
            throw new IllegalArgumentException("bucketDuration must be positive");
        }
        // raw row 가져와 Java 단에서 group by — DB 호환 (H2 / Postgres) 위해 SQL date_trunc 미사용.
        // 운영자가 from/to 로 범위를 적절히 조절해 row 수가 적당 (예: 1주일 = 수천건) 한 시나리오 가정.
        List<DeliveryAttemptEntity> rows =
                jpa.findForStats(DeliveryStatus.EXHAUSTED, from, to);
        Map<StatKey, Long> bucket = new HashMap<>();
        for (DeliveryAttemptEntity e : rows) {
            long t = e.getCreatedAt().toEpochMilli();
            long bucketStartMs = (t / bucketMs) * bucketMs;
            Instant bucketStart = Instant.ofEpochMilli(bucketStartMs);
            String errorClass = DlqErrorClass.classify(e.getFailureReason());
            StatKey key = new StatKey(bucketStart, e.getChannelType(), errorClass);
            bucket.merge(key, 1L, Long::sum);
        }
        List<DlqStatRow> result = new ArrayList<>(bucket.size());
        for (Map.Entry<StatKey, Long> en : bucket.entrySet()) {
            StatKey k = en.getKey();
            result.add(new DlqStatRow(k.bucketStart, k.channelType, k.errorClass, en.getValue()));
        }
        return result;
    }

    private record StatKey(Instant bucketStart, ChannelType channelType, String errorClass) {}
}
