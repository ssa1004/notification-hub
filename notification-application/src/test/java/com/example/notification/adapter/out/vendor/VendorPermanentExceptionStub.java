package com.example.notification.adapter.out.vendor;

/**
 * 테스트 전용 stub — 실제 {@code VendorPermanentException} 은 adapter-out 모듈에 있으므로
 * application 모듈 테스트에서 직접 의존하지 못함. {@link DispatchDeliveryService} 가 클래스
 * simple name 에 "Permanent" 포함 여부로 분기하므로 동일 패턴 클래스명만 맞추면 충분.
 */
public class VendorPermanentExceptionStub extends RuntimeException {
    public VendorPermanentExceptionStub(String message) {
        super(message);
    }
}
