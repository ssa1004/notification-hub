# Contributing

본 저장소의 개발 흐름과 commit 규칙을 정리한 문서입니다.

## 브랜치 전략

GitHub Flow 를 따릅니다. `main` 은 항상 배포 가능한 상태로 유지되며, 모든 작업은 feature
브랜치에서 진행됩니다.

```
main (protected)
  ├── feature/idempotency-key       ← 기능 브랜치
  ├── fix/outbox-relay-timeout
  └── docs/update-readme
```

흐름은 `git checkout -b feature/<짧은-설명>` → 작업 → PR → 코드 리뷰 + CI 통과 → Squash and
merge 입니다. 머지 후 feature 브랜치는 즉시 삭제합니다.

## Commit 메시지

Conventional Commits 형식을 따릅니다.

```
<type>(<scope>): <짧은 설명, 50자 이내>

<상세 설명, 한 줄에 72자 이내>
- 무엇이 / 왜 변경되었는지
- 영향받는 모듈
```

사용하는 type: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`.
scope 에는 모듈명 (`domain`, `application`, `adapter-out`, `adapter-in`, `bootstrap` 등) 이
들어갑니다.

알림 fan-out, 멱등성, Outbox, retry / DLQ 가 도메인의 핵심이므로 관련 commit 이 자주
발생합니다.

### 예시

```
feat(application): Idempotency-Key 기반 알림 발송 멱등성 처리

- SendNotificationService 진입 시 Redis SETNX 로 키 점유
- 중복 요청은 DuplicateRequestException → HTTP 409
- TTL 24시간 (운영 정책상 같은 키 재사용 금지 기간)
```

```
fix(adapter-out): Outbox relay 가 publish 실패 row 를 markPublished 처리하던 버그

OutboxRelay#run 에서 send() 가 ListenableFuture 인 점을 무시하고 즉시
markPublished 를 호출하여 vendor 호출 실패 시 메시지가 영구 누락되던 문제.
get(timeout) 으로 동기 대기 + 실패 시 next polling 에서 재시도하도록 수정.
```

## Commit 단위

한 commit 은 한 가지 논리적 변경을 담는 것을 원칙으로 합니다. 새 기능 + 리팩터링 + 버그
수정이 한 commit 에 같이 포함되어 있다면 거의 항상 분리 가능합니다. WIP commit 은 PR 머지
전에 squash 합니다.

## 테스트

PR 전 `./gradlew check` 통과가 필수입니다. 빠른 단위 테스트만 별도로 실행하려면 다음 명령을
사용합니다.

- 도메인: `:notification-domain:test`
- 애플리케이션: `:notification-application:test`
- 통합 시나리오 (Postgres + Redis + Kafka Testcontainer 필요): `:e2e-tests:test`

## 코드 스타일

- Java: Google Java Format 또는 IntelliJ default
- 주석 / 문서는 자연스러운 한국어 (영어 직역체 지양)
- 도메인 모델은 `final class` + 모든 필드 `private final` (불변)
