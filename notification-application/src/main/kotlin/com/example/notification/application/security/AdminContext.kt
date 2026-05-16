package com.example.notification.application.security

/**
 * 현재 호출 thread 의 admin 권한 여부. adapter-in 의 filter 가 요청별로
 * [set] → use case 가 [isAdmin] 으로 가드.
 *
 * ThreadLocal 기반. virtual thread 와도 호환 (Java 21 부터 ThreadLocal 가 carrier 가 아닌
 * virtual thread 에 종속).
 */
object AdminContext {

    private val ADMIN: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    @JvmStatic
    fun set(admin: Boolean) {
        ADMIN.set(admin)
    }

    @JvmStatic
    fun isAdmin(): Boolean = java.lang.Boolean.TRUE == ADMIN.get()

    @JvmStatic
    fun clear() {
        ADMIN.remove()
    }
}
