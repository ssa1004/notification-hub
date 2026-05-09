package com.example.notification.application.security;

/**
 * 현재 호출 thread 의 admin 권한 여부. adapter-in 의 filter 가 요청별로
 * {@link #set(boolean)} → use case 가 {@link #isAdmin()} 으로 가드.
 *
 * <p>ThreadLocal 기반. virtual thread 와도 호환 (Java 21 부터 ThreadLocal 가 carrier 가 아닌
 * virtual thread 에 종속).
 */
public final class AdminContext {

    private static final ThreadLocal<Boolean> ADMIN = ThreadLocal.withInitial(() -> false);

    private AdminContext() {}

    public static void set(boolean admin) {
        ADMIN.set(admin);
    }

    public static boolean isAdmin() {
        return Boolean.TRUE.equals(ADMIN.get());
    }

    public static void clear() {
        ADMIN.remove();
    }
}
