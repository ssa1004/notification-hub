package com.example.notification.adapter.in.security;

import com.example.notification.application.security.AdminContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /api/v1/admin/** 요청만 X-Admin-Token 헤더 검증. 헤더 값이 application yml 의
 * {@code admin.auth.token} 과 일치하면 {@link AdminContext} 에 admin=true 세팅.
 *
 * <p>비교는 timing-safe ({@link MessageDigest#isEqual}). 다른 endpoint 는 그대로 통과 — Spring
 * Security 도입 전 단계의 가벼운 가드. 실제 운영은 Spring Security + OIDC + role 기반으로 교체.
 *
 * <p>{@code admin.auth.token} 미설정이면 모든 admin 요청을 거절 — 사고 방지 (default-deny).
 */
@Slf4j
@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Admin-Token";
    static final String ADMIN_PATH_PREFIX = "/api/v1/admin/";

    private final byte[] tokenBytes;

    public AdminAuthFilter(@Value("${admin.auth.token:}") String token) {
        this.tokenBytes =
                (token == null || token.isBlank())
                        ? new byte[0]
                        : token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean adminPath = request.getRequestURI().startsWith(ADMIN_PATH_PREFIX);
        if (!adminPath) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HEADER);
        boolean ok = isValid(header);
        AdminContext.set(ok);
        try {
            chain.doFilter(request, response);
        } finally {
            AdminContext.clear();
        }
    }

    private boolean isValid(String header) {
        if (tokenBytes.length == 0) {
            log.warn("admin.auth.token 미설정 — 모든 admin 요청 거절 (default-deny)");
            return false;
        }
        if (header == null) return false;
        byte[] given = header.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(given, tokenBytes);
    }
}
