package com.example.notification.application.service;

import com.example.notification.domain.channel.Channel;
import com.example.notification.domain.channel.ChannelType;
import com.example.notification.domain.notification.NotificationKind;
import com.example.notification.domain.preference.UserPreference;
import com.example.notification.domain.recipient.Recipient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 한 알림의 대상 채널을 최종 결정하는 도메인 서비스. 입력:
 *
 * <ul>
 *   <li>Recipient — 사용자가 가진 raw 채널들 (이메일, 전화번호, push token)
 *   <li>UserPreference — 종류별 opt-out / 우선 채널 / DND
 *   <li>NotificationKind — mandatory / DND 우회 여부
 *   <li>now — DND 판정 기준 시각
 * </ul>
 *
 * <p>적용 순서:
 * <ol>
 *   <li>opt-out 으로 차단된 종류면 빈 리스트 (즉 SUPPRESSED)
 *   <li>preferredChannels 가 있으면 그 ChannelType 으로 필터 / 없으면 모든 raw 채널
 *   <li>DND 시간대면 bypass 허용 종류 외엔 채널 단위 차단
 *   <li>KAKAO_ALIMTALK 은 야간 항상 차단 (vendor 정책)
 * </ol>
 */
@Component
public class ChannelResolver {

    public List<Channel> resolve(
            Recipient recipient,
            UserPreference preference,
            NotificationKind kind,
            Instant now) {

        // 1. opt-out
        if (!preference.isAllowed(kind)) {
            return List.of();
        }

        // 2. 후보: 사용자 raw 채널 전체
        Set<ChannelType> preferred = preference.preferredChannelsFor(kind);
        List<Channel> candidates = new ArrayList<>();
        for (Channel ch : recipient.channels()) {
            if (preferred.isEmpty() || preferred.contains(ch.type())) {
                candidates.add(ch);
            }
        }

        // 3. DND
        boolean inQuietHours =
                preference.quietHours() != null
                        && preference.quietHours().contains(now, preference.timezone());

        if (inQuietHours && kind.respectsQuietHours()) {
            return List.of();
        }

        // 4. KAKAO_ALIMTALK 야간 차단 — kind 가 DND 우회해도 vendor 정책상 야간 reject.
        if (inQuietHours) {
            candidates.removeIf(ch -> !ch.type().allowedAtNight());
        }

        // 결과의 순서는 ChannelType 정의 순서로 정렬 — 같은 입력에 같은 출력 (테스트 결정성).
        EnumSet<ChannelType> seenTypes = EnumSet.noneOf(ChannelType.class);
        List<Channel> ordered = new ArrayList<>();
        for (ChannelType t : ChannelType.values()) {
            for (Channel ch : candidates) {
                if (ch.type() == t && seenTypes.add(t)) {
                    ordered.add(ch);
                    break; // 한 타입당 하나의 raw 채널만 사용 (멀티 디바이스 push 는 별도 처리)
                }
            }
        }
        return ordered;
    }

    public String suppressionReason(
            Recipient recipient, UserPreference preference, NotificationKind kind, Instant now) {
        if (!preference.isAllowed(kind)) {
            return "OPT_OUT";
        }
        boolean inQuietHours =
                preference.quietHours() != null
                        && preference.quietHours().contains(now, preference.timezone());
        if (inQuietHours && kind.respectsQuietHours()) {
            return "QUIET_HOURS";
        }
        return "NO_ELIGIBLE_CHANNEL";
    }
}
