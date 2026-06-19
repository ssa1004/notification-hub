# Diagrams

README 의 핵심 Mermaid 다이어그램을 정적 SVG 로 렌더링해 둔 것입니다. GitHub 은 README
안의 Mermaid 를 직접 렌더하지만, 외부 뷰어 / PDF / 슬라이드 등 Mermaid 를 지원하지 않는
환경에서 참조할 수 있도록 SVG 사본을 둡니다. **README 의 Mermaid 블록이 단일 진실값**이고,
아래 SVG 는 그로부터 파생됩니다.

| SVG | 내용 | README 출처 |
|---|---|---|
| [`delivery-flow.svg`](delivery-flow.svg) | 발송 흐름 — 요청 1건이 멱등성 점유 → fan-out → Outbox → Kafka → vendor 까지 | README "발송 흐름" |
| [`module-structure.svg`](module-structure.svg) | 모듈 의존 방향 (adapter → application → domain) | README "모듈 구조" |

## 재생성

[mermaid-cli](https://github.com/mermaid-js/mermaid-cli) 로 렌더합니다 (Node 필요).

```bash
# README 의 해당 Mermaid 블록을 *.mmd 로 추출한 뒤:
npx -y @mermaid-js/mermaid-cli -i delivery-flow.mmd    -o docs/diagrams/delivery-flow.svg
npx -y @mermaid-js/mermaid-cli -i module-structure.mmd -o docs/diagrams/module-structure.svg
```
