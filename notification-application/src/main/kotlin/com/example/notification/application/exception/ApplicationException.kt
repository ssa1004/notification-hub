package com.example.notification.application.exception

/** Application 계층 공통 예외 부모. adapter-in 에서 HTTP 매핑. */
abstract class ApplicationException : RuntimeException {
    protected constructor(message: String) : super(message)
    protected constructor(message: String, cause: Throwable?) : super(message, cause)
}
