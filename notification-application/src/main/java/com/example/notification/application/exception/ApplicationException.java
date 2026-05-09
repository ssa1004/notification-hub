package com.example.notification.application.exception;

/** Application 계층 공통 예외 부모. adapter-in 에서 HTTP 매핑. */
public abstract class ApplicationException extends RuntimeException {

    protected ApplicationException(String message) {
        super(message);
    }

    protected ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
