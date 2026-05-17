package com.example.notification.adapter.`in`.security

import com.example.notification.application.security.AdminContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * `/api/v1/admin/...` 요청만 X-Admin-Token 헤더 검증. 헤더 값이 application yml 의
 * `admin.auth.token` 과 일치하면 [AdminContext] 에 admin=true 세팅.
 *
 * 비교는 timing-safe ([MessageDigest.isEqual]). 다른 endpoint 는 그대로 통과 — Spring
 * Security 도입 전 단계의 가벼운 가드. 실제 운영은 Spring Security + OIDC + role 기반으로 교체.
 *
 * `admin.auth.token` 미설정이면 모든 admin 요청을 거절 — 사고 방지 (default-deny).
 */
@Component
class AdminAuthFilter(
    @Value("\${admin.auth.token:}") token: String?,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    private val tokenBytes: ByteArray =
        if (token.isNullOrBlank()) ByteArray(0) else token.toByteArray(StandardCharsets.UTF_8)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val adminPath = request.requestURI.startsWith(ADMIN_PATH_PREFIX)
        if (!adminPath) {
            chain.doFilter(request, response)
            return
        }

        val header = request.getHeader(HEADER)
        val ok = isValid(header)
        AdminContext.set(ok)
        try {
            chain.doFilter(request, response)
        } finally {
            AdminContext.clear()
        }
    }

    private fun isValid(header: String?): Boolean {
        if (tokenBytes.isEmpty()) {
            log.warn("admin.auth.token 미설정 — 모든 admin 요청 거절 (default-deny)")
            return false
        }
        if (header == null) return false
        val given = header.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(given, tokenBytes)
    }

    companion object {
        const val HEADER = "X-Admin-Token"
        const val ADMIN_PATH_PREFIX = "/api/v1/admin/"
    }
}
