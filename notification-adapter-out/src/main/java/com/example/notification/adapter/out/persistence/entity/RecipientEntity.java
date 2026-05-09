package com.example.notification.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 알림 hub 가 read-only 로 보는 사용자 정보.
 *
 * <p>실제 운영에선 별도 user/auth service 의 read replica 또는 Kafka CDC 로 동기화하는 패턴.
 * 이 hub 자체는 *master 가 아님* — 등록 endpoint 도 운영자/내부 시스템 전용.
 *
 * <p>채널은 {@code channels_json} 에 [{type:..., address:...}, ...] 형태로 저장.
 */
@Entity
@Table(name = "recipient")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecipientEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "channels_json", nullable = false, length = 4000)
    private String channelsJson;

    @Column(name = "locale", nullable = false, length = 16)
    private String locale;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;
}
