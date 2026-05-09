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
 * UserPreference 의 영속 표현.
 *
 * <p>{@code allowedJson}, {@code preferredJson} 은 Map 을 JSON 직렬화한 텍스트 (Postgres 에서는
 * jsonb 로 매핑하면 더 좋지만 H2 호환을 위해 text 로). 변경 빈도 낮고 단일 사용자만 본인 row
 * 를 read 하므로 부담 없음.
 */
@Entity
@Table(name = "user_preference")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceEntity {

    @Id
    @Column(name = "recipient_id", length = 128)
    private String recipientId;

    @Column(name = "allowed_json", nullable = false, length = 2000)
    private String allowedJson;

    @Column(name = "preferred_json", nullable = false, length = 2000)
    private String preferredJson;

    @Column(name = "quiet_start", length = 8)
    private String quietStart;

    @Column(name = "quiet_end", length = 8)
    private String quietEnd;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;
}
