package com.example.notification.adapter.out.vendor;

import com.example.notification.application.port.out.InvalidRecipientFailure;

/**
 * vendor 측 영구 오류 중 수신자 식별자가 무효인 케이스 — FCM NOT_REGISTERED, Twilio 21211
 * (잘못된 번호) 등. retry 무의미 + 같은 식별자로의 미래 발송도 의미 없음.
 *
 * <p>application 단 DispatchDeliveryService 는 이 신호를 받으면 PUSH 채널의 경우 device
 * token 을 비활성화. {@link VendorPermanentException} 의 subtype 이라 retry/permanent
 * 분기는 부모 클래스로 흡수되고, deactivate 분기만 더 좁게 발동.
 */
public class VendorInvalidRecipientException extends VendorPermanentException
        implements InvalidRecipientFailure {
    public VendorInvalidRecipientException(String message) {
        super(message);
    }
}
