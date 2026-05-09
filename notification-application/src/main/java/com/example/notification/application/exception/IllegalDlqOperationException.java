package com.example.notification.application.exception;

/** DLQ 작업이 현재 attempt 상태에서 허용되지 않음 (EXHAUSTED 가 아닌 항목 replay 등). */
public class IllegalDlqOperationException extends ApplicationException {
    public IllegalDlqOperationException(String message) {
        super(message);
    }
}
