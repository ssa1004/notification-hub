package com.example.notification.application.dto

import java.time.Duration
import java.time.Instant

/**
 * DLQ stats — 시간 bucket 별 EXHAUSTED 개수 + 채널/에러 종류 별 cardinality.
 *
 * 운영 화면에서 시간대별 추세 + 어느 vendor 가 가장 많이 실패 중인지 한눈에. 일반 page-by-page
 * list 로는 보기 어려움.
 */
@JvmRecord
data class DlqStats(
    val from: Instant,
    val to: Instant,
    val bucketDuration: Duration,
    val totalCount: Long,
    val byBucket: List<BucketCount>,
    val byChannel: List<KeyedCount>,
    val byErrorClass: List<KeyedCount>,
) {

    @JvmRecord
    data class BucketCount(
        val bucketStart: Instant,
        val count: Long,
    )

    /** channel / errorClass 등 string key 의 count. */
    @JvmRecord
    data class KeyedCount(
        val key: String,
        val count: Long,
    )
}
