# ADR-0005: 사용자 선호도 — 채널 우선순위 / opt-out / 방해금지

## 상태
적용

## 배경
한 알림이 여러 채널로 동시에 나가면 사용자는 같은 내용을 4번 받는 셈. 그렇다고 한 채널만
쓰자니 그 채널이 비활성 (전화번호 미등록, push token 만료) 일 때 알림이 사라짐. 그리고 야간
알림은 사용자 만족도를 크게 떨어뜨림 — 한국 사용자 설문 기준 22:00~08:00 마케팅 알림은
앱 삭제 1순위 사유.

채널 결정에는 세 가지 입력이 동시에 작용해야 합니다.

1. 사용자가 가진 raw 채널들 (`Recipient#channels`)
2. 사용자 선호도 (`UserPreference` — 종류별 opt-out, 우선 채널, DND 시간)
3. 알림의 종류 (`NotificationKind` — 마케팅 / 거래 / 보안 / 공지)

## 결정
`ChannelResolver` 가 도메인 서비스로 다음 순서를 적용:

```
1. NotificationKind 가 사용자 opt-out 대상인가?
   → Yes → 빈 리스트 (= SUPPRESSED, OPT_OUT 사유)
2. preferredChannels 가 비어있지 않으면 그 ChannelType 으로 raw 채널 필터
3. 현재 시각이 사용자 timezone 기준 quietHours 안인가?
   → Yes 이면서 kind 가 DND 우회 안 함 → 빈 리스트 (SUPPRESSED, QUIET_HOURS)
4. quietHours 안이면 KAKAO_ALIMTALK 채널 제거 (kind 무관 vendor 정책)
5. 한 ChannelType 당 최대 1개 raw 채널 선택, ChannelType 정의 순서 (PUSH→EMAIL→SMS→KAKAO) 로 정렬
```

`NotificationKind` 자체에 정책 메타가 박혀 있음:
- `MARKETING` — opt-out 가능, DND 적용
- `TRANSACTIONAL` — opt-out 불가 (법적 의무), DND 적용
- `SECURITY` — opt-out 불가, DND **우회** (OTP 등 보안 알림은 새벽이라도 가야 함)
- `SERVICE` — opt-out 가능, DND 적용

## 결과
- 한 알림 = 한 사용자 한 채널 1번 (멱등 + 비중복)
- DND / opt-out 거절은 audit log 에 사유 (`OPT_OUT`, `QUIET_HOURS`, `NO_ELIGIBLE_CHANNEL`)
  로 기록 — 사용자 문의 (왜 안 왔는지) 대응에 필수
- 채널 우선순위가 명시되면 (예: 마케팅은 EMAIL 만) 다른 채널은 시도 자체 없음 = vendor
  비용 절감
- (단점) 한 ChannelType 당 한 채널만 선택. PUSH 의 multi-device fan-out 은 별도 단계
  (`expandPushFanOut`) 로 분리 — ADR 0013 참고
- (단점) DND 시간이 기간 1개로 단순화 — 실제론 평일/주말 다른 윈도우, 휴일 정책 등이
  필요할 수 있음

## 다시 검토할 시점
- DND 가 다중 윈도우 / 평일·주말 분리 / 휴일 캘린더 연동까지 가야 하면 `QuietHours` 를
  `QuietHoursPolicy` 로 확장
