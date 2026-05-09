package com.example.notification.adapter.in.security;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * vendor 별 webhook HMAC 서명 secret. application.yml 에서:
 *
 * <pre>
 * webhook:
 *   secrets:
 *     fcm: ${WEBHOOK_SECRET_FCM:}
 *     ses: ${WEBHOOK_SECRET_SES:}
 *     twilio: ${WEBHOOK_SECRET_TWILIO:}
 *     kakao: ${WEBHOOK_SECRET_KAKAO:}
 * </pre>
 *
 * <p>vendor 식별은 요청 헤더 {@code X-Notification-Hub-Vendor} 로. 등록 안 된 vendor 가
 * 들어오거나 secret 미설정이면 fail-closed (요청 거절).
 */
@Component
@ConfigurationProperties(prefix = "webhook")
public class WebhookSecrets {

    private final Map<String, String> secrets = new HashMap<>();

    public Map<String, String> getSecrets() {
        return secrets;
    }

    /** vendor 의 secret 반환. 없거나 빈 문자열이면 null. */
    public String secretFor(String vendor) {
        if (vendor == null) return null;
        String s = secrets.get(vendor.toLowerCase());
        return (s == null || s.isBlank()) ? null : s;
    }
}
