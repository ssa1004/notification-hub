package com.example.notification.application.port.out;

/**
 * 영구 실패 중에서도 수신자 식별자 자체가 무효라는 신호. 단말 토큰이 unregister 됐거나
 * 전화번호 형식이 잘못된 케이스 등 — 같은 식별자로 다시 보내도 영원히 같은 결과.
 *
 * <p>application 단의 DispatchDeliveryService 가 PUSH 채널에서 이 마커를 만나면 device token
 * 을 비활성화한다. 같은 영구 실패라도 payload 형식 오류 (FCM INVALID_ARGUMENT, SES
 * MessageRejected 등) 는 토큰/주소 자체는 멀쩡하므로 비활성화하면 안 된다 — 그래서
 * {@link PermanentDeliveryFailure} 보다 좁은 마커로 분리.
 *
 * <p><b>분류 가이드</b>:
 *
 * <ul>
 *   <li>InvalidRecipientFailure — FCM NOT_REGISTERED, Twilio 21211 (잘못된 번호), SES
 *       AddressBlacklisted 등 식별자 무효.
 *   <li>PermanentDeliveryFailure (이쪽으로만 분류) — FCM INVALID_ARGUMENT, Kakao
 *       TEMPLATE_NOT_FOUND, vendor 정책 차단 (NIGHT_TIME_BLOCKED) 등 재전송은 무의미하지만
 *       식별자는 유효한 경우.
 * </ul>
 */
public interface InvalidRecipientFailure extends PermanentDeliveryFailure {}
