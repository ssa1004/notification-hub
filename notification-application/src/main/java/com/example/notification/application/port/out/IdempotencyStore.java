package com.example.notification.application.port.out;

import com.example.notification.domain.shared.IdempotencyKey;
import java.time.Duration;

/**
 * 멱등성 키 점유 store. Redis SETNX 로 구현.
 *
 * <p>같은 키가 동시에 두 번 들어오면 한 쪽만 성공해야 하므로 set-if-absent 의 *원자성* 이
 * 핵심. 분산 환경에서 application 단의 Map 은 못 씀 (인스턴스마다 별도라 race).
 */
public interface IdempotencyStore {

    /**
     * 키를 점유합니다. 기존에 없으면 점유 후 true, 이미 있으면 false.
     *
     * @param key 멱등성 키
     * @param ttl 키 자동 만료 시간 (재사용 금지 기간)
     */
    boolean tryAcquire(IdempotencyKey key, Duration ttl);

    /** 디버그/테스트용 — 운영에선 거의 호출 안 함. */
    void release(IdempotencyKey key);
}
