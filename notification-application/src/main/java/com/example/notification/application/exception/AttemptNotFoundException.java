package com.example.notification.application.exception;

import java.util.UUID;

public class AttemptNotFoundException extends ApplicationException {
    public AttemptNotFoundException(UUID id) {
        super("deliveryAttempt not found: " + id);
    }
}
