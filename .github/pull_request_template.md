<!--
PR 제목은 Conventional Commits 형식을 따릅니다 (예: feat(application): ...).
Squash merge 시 이 제목이 commit 메시지가 됩니다. CONTRIBUTING.md 참고.
-->

## 변경 요약

<!-- 무엇을 / 왜 바꿨는지 1~3 문장으로. -->

## 변경 유형

- [ ] feat — 새 기능
- [ ] fix — 버그 수정
- [ ] refactor — 동작 변화 없는 구조 개선
- [ ] perf — 성능 개선
- [ ] test — 테스트 추가/수정
- [ ] docs — 문서
- [ ] chore / ci / build — 빌드·CI·의존성

## 영향 범위

<!-- 영향받는 모듈에 체크. -->

- [ ] notification-domain
- [ ] notification-application
- [ ] notification-adapter-in
- [ ] notification-adapter-out
- [ ] notification-bootstrap
- [ ] infrastructure / helm (배포)
- [ ] CI / 의존성

## 체크리스트

- [ ] `./gradlew check` 통과 (또는 관련 모듈 테스트)
- [ ] 새 동작에 대한 테스트 추가 / 기존 테스트 갱신
- [ ] DB 마이그레이션(Flyway) 또는 설정 변경 시 README / values 문서 반영
- [ ] 배포 매니페스트(helm / k8s) 변경 시 `helm lint` + `kubeconform` 통과
- [ ] 시크릿 / 자격증명을 코드·로그에 노출하지 않음

## 관련 이슈

<!-- Closes #이슈번호 -->
