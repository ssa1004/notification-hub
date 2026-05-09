package com.example.notification.adapter.out.vendor;

import com.example.notification.application.port.out.PermanentDeliveryFailure;

/**
 * vendor 측 영구 오류 — invalid token, malformed payload 등. retry 해도 같은 결과.
 *
 * <p>{@link PermanentDeliveryFailure} 마커를 구현 — application 단의 DispatchDeliveryService
 * 가 즉시 markFailed + (PUSH 라면) device token 비활성화 분기로 보낸다. 도메인 retry 카운트는
 * 직전과 같이 유지되어 다음 polling 에서 EXHAUSTED 로 직행.
 */
public class VendorPermanentException extends RuntimeException implements PermanentDeliveryFailure {
    public VendorPermanentException(String message) {
        super(message);
    }
}
