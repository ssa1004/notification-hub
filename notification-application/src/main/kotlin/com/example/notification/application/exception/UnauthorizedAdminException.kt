package com.example.notification.application.exception

/** admin role 이 아닌 caller 가 admin endpoint 를 호출. adapter-in 에서 401/403 매핑. */
class UnauthorizedAdminException(message: String) : ApplicationException(message)
