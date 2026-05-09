package com.example.notification.application.exception;

/** 같은 Idempotency-Key 로 중복 요청. HTTP 409. */
public class DuplicateRequestException extends ApplicationException {

    public DuplicateRequestException(String idempotencyKey) {
        super("duplicate request: idempotencyKey=" + idempotencyKey);
    }
}
