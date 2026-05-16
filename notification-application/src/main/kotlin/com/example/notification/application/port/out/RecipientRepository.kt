package com.example.notification.application.port.out

import com.example.notification.domain.recipient.Recipient
import com.example.notification.domain.recipient.RecipientId
import java.util.Optional

/**
 * 사용자(수신자) 조회 port. 알림 hub 자체에서는 raw 채널 (이메일/전화번호) 까지만 필요해서
 * RecipientId + 채널 + locale + timezone 만 알면 됩니다.
 *
 * 실제로는 별도 user/auth service 가 master 이고 여기서는 read-replica 또는 cache 만 두는
 * 패턴이 일반적. 이 hub 는 복제본 read 만 한다고 가정.
 */
interface RecipientRepository {

    fun findById(id: RecipientId): Optional<Recipient>
}
