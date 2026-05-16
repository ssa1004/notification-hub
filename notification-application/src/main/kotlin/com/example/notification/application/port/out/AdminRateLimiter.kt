package com.example.notification.application.port.out

import com.example.notification.domain.shared.RateLimitDecision

/**
 * admin (운영) endpoint 의 호출자별 token bucket. 일반 [RateLimiter] 는 recipient × channel 단위 —
 * admin 은 호출자가 사람이라 channel 개념 없음. IP + path key 기반.
 *
 * 한도 / 윈도우는 어댑터 단 config 가 정함 (default 분당 60회). [scope] 는 같은 IP 라도 다른
 * admin 작업 그룹은 별도 카운트 (예: list 와 bulk-replay 를 분리).
 */
interface AdminRateLimiter {

    fun tryConsume(scope: String, callerKey: String): RateLimitDecision
}
