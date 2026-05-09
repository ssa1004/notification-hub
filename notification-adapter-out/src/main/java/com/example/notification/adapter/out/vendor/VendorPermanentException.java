package com.example.notification.adapter.out.vendor;

/**
 * vendor 측 영구 오류 — invalid token, malformed payload 등. retry 해도 같은 결과.
 *
 * <p>이 예외가 던져지면 worker 가 retry 없이 즉시 markFailed 하고 retry 카운트는 직전과 같이
 * 유지 (도메인이 increment) → 다음 polling 에서 EXHAUSTED 로 직행.
 */
public class VendorPermanentException extends RuntimeException {
    public VendorPermanentException(String message) {
        super(message);
    }
}
