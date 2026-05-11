# 보안 정책

## 저장소 성격

이 저장소는 학습 / 포트폴리오 목적이며 운영 환경에 직접 배포되지 않습니다. 포함된 vendor
adapter (FCM / SES / Twilio / Kakao 알림톡) 도 mock 구현입니다. 따라서 일반적인 운영
서비스 수준의 SLA 는 제공하지 않습니다.

## 지원 범위

| 항목 | 범위 |
|---|---|
| 코드 | `main` 브랜치 최신 |
| 의존성 | Spring Boot 3.4.x patch, 직접 선언한 라이브러리의 동일 minor patch |
| Container 이미지 | `ghcr.io/ssa1004/notification-hub:main-*` 최근 태그 |

## 취약점 보고

취약점을 발견하셨다면 GitHub Issue 가 아닌 아래 방법으로 비공개로 알려 주세요.

- GitHub Security Advisory: <https://github.com/ssa1004/notification-hub/security/advisories/new>
- 메일: `wittyahn@users.noreply.github.com`

다음 정보를 함께 주시면 재현이 빠릅니다.

- 영향받는 파일 / 모듈 / 커밋 SHA
- 재현 절차 (가능하면 PoC)
- 영향 범위 (정보 노출 / 권한 상승 / 서비스 거부 등)
- 발견 환경 (OS / JDK / Spring Boot 버전)

## 응답 시간 (best-effort)

학습 저장소이므로 24/7 응답을 보장하지 않습니다. 아래는 목표치입니다.

| 단계 | 목표 |
|---|---|
| 접수 확인 | 영업일 기준 3일 이내 |
| 영향도 평가 | 영업일 기준 7일 이내 |
| 패치 / 공지 | 심각도에 따라 분기, 가능한 한 빠르게 |

## 범위 외

- 직접 의존하지 않는 transitive 의존성의 취약점 (upstream 측에서 수정 후 BOM 갱신 시 반영)
- vendor mock 의 보안 미흡 (실제 SDK 로 교체 시 해당 SDK 보안 가이드 준수)
- 데모용 docker-compose / Helm dev 프로파일의 평문 자격 증명 (의도된 dev 편의)
- DoS — 학습 환경의 리소스 한계 자체

## 보안 관련 코드 진입점

검토에 도움이 될 만한 위치를 모았습니다.

- 인증 — `notification-bootstrap` 의 `application-prod.yml` JWT issuer 설정 (운영 활성화는 후속)
- HMAC 검증 — `notification-adapter-in` 의 webhook controller (ADR 0014)
- 멱등성 — `Idempotency-Key` 헤더 + Redis SETNX (`SendNotificationService`)
- Rate limit — `application` 모듈의 token bucket
- Secret 관리 — `helm/notification-hub/templates/secret.yaml` (`secrets.mode=external` 권장)
