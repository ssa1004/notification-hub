package com.example.notification.application.exception

/** 같은 Idempotency-Key 로 중복 요청. HTTP 409. */
class DuplicateRequestException(idempotencyKey: String) :
    ApplicationException("duplicate request: idempotencyKey=$idempotencyKey")
