package com.example.notification.adapter.out.vendor

/** vendor 측 일시 오류 (5xx, network timeout). Resilience4j retry 대상. */
open class VendorTransientException(message: String) : RuntimeException(message)
