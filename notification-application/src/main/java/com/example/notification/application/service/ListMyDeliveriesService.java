package com.example.notification.application.service;

import com.example.notification.application.dto.DeliveryHistoryPage;
import com.example.notification.application.port.in.ListMyDeliveriesUseCase;
import com.example.notification.application.port.out.NotificationRepository;
import com.example.notification.domain.recipient.RecipientId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListMyDeliveriesService implements ListMyDeliveriesUseCase {

    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 20;

    private final NotificationRepository repository;

    @Override
    @Transactional(readOnly = true)
    public DeliveryHistoryPage list(String recipientId, UUID cursor, int limit) {
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return repository.findHistory(new RecipientId(recipientId), cursor, safeLimit);
    }
}
