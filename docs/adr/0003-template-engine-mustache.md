# ADR-0003: 템플릿 엔진 — Mustache 선택

## 상태
적용

## 배경
알림 템플릿은 `{name}님 주문 {orderNo} 가 출고되었습니다` 같은 placeholder 치환이 핵심.
복잡한 분기 (if / for) 가 거의 필요 없고, 사용자/운영자가 직접 등록하므로 *문법 단순함* 이
중요. 후보:

| 엔진 | placeholder 문법 | 장점 | 단점 |
|---|---|---|---|
| Thymeleaf | `[(${name})]`, `th:if` 등 | Spring 통합 강함, HTML 렌더링 | 문법 복잡, 의존성 큼, 사용자 작성 어려움 |
| Freemarker | `${name}`, `<#if>` | 표현력 풍부 | 보안 (script injection) 학습 곡선, vendor 의 알림톡 템플릿과 문법 충돌 |
| Mustache | `{{var}}` | logic-less, 문법 1쪽, lib 작음 (jar 50KB) | 분기/반복 거의 불가능 (의도된 제약) |
| Plain replace | `{var}` → 직접 String.replace | 의존성 0 | escape / 중첩 / fallback 처리 직접 구현 |

## 결정
**Mustache** (`com.github.spullara.mustache.java:compiler`) 를 채택. 단, 사용자 작성 편의를
위해 우리 도메인의 placeholder 문법은 *단일 중괄호* (`{name}`) 로 두고 렌더링 직전에 이중
중괄호 (`{{name}}`) 로 변환 후 컴파일.

## 결과
- 운영자가 템플릿 등록 시 `{var}` 만 알면 됨 — 가르치기 쉬움
- 분기 / 반복 불가능 = vendor 측 사전 검수가 단순. 카카오 알림톡은 어차피 분기 / 반복 금지
  (정책)
- 템플릿 누락 변수는 도메인이 사전 검증 (`Template#verifyPayloadCovers`) — Mustache 가 빈
  문자열로 silently 치환하는 단점 보완
- (단점) HTML escape 가 필요하면 별도 처리 — 지금은 PUSH/SMS/알림톡 raw text 위주라 무시.
  EMAIL HTML 템플릿이 늘어나면 별도 sanitizer layer 추가 검토

## 다시 검토할 시점
- 템플릿에 조건부 (예: 환불 금액이 있을 때만 추가 문구) 가 빈번해지면 Freemarker 검토
- HTML 이메일이 주류가 되면 escape / sanitizer + Thymeleaf 도 후보
