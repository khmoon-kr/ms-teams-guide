# MS Teams 메시지·카드 백엔드 JSON 형식 및 Workflows 구현 가이드

Microsoft Teams의 **채널(Channel)** 및 **그룹 채팅(Group Chat)** 을 대상으로 알림, 액션 카드, Adaptive Card를 송수신·처리하기 위한 **백엔드 JSON 스펙**과 **Microsoft Teams Workflows(Power Automate)** 기반 구현 방법을 주제별로 분리하여 정리한 문서 모음입니다.

---

## 1. 아키텍처 개요 및 2026년 기준 주요 변경사항

> [!IMPORTANT]
> **Office 365 Connectors(레거시 Incoming Webhook) 은퇴 안내**
> Microsoft Teams의 기존 Office 365 Connectors(레거시 커넥터 기반 Incoming Webhook)는 **2026년 5월 완전히 은퇴(Retired)** 되었습니다.
> 현재는 **Teams Workflows 앱(Power Automate 기반)** 의 `When a Teams webhook request is received` 트리거 또는 **Teams Bot(Teams SDK / Bot Framework)**, **Microsoft Graph API**를 사용해야 합니다.

### 연동 방식별 비교

| 연동 방식 | 전송 대상 | 카드 지원 범위 | 양방향(사용자 응답) 처리 | 주요 용도 |
| :--- | :--- | :--- | :--- | :--- |
| **Workflows Incoming Webhook** | 채널, 그룹 채팅, 1:1 채팅 | Adaptive Card(최대 v1.6), MessageCard(렌더링만) | 웹훅 단독 불가 → Workflows 내 `Post adaptive card and wait for a response` 또는 `When someone responds...` 조합 필요 | CI/CD, 모니터링 알림, 외부 시스템 Webhook 연동 |
| **Workflows (Power Automate 커넥터)** | 표준/공유 채널, 그룹 채팅, 1:1 채팅 | 텍스트/HTML, Adaptive Card | `Post adaptive card and wait for a response`, `When someone responds to an adaptive card`, `For a selected message (V2)` | 노코드/로우코드 승인 플로우, **동적 채널/그룹 채팅 생성** 후 알림 |
| **Teams Bot (Bot Framework / Teams SDK)** | 채널(스레드 포함), 그룹 채팅, 1:1 채팅 | 모든 카드(Adaptive, Hero, Thumbnail, List, Receipt, Sign-in, Carousel, O365 Connector) | `Action.Execute` (`invoke` / `adaptiveCard/action`), `Action.Submit`, `messageBack`, `imBack`, `task/fetch` | 맞춤형 인터랙티브 챗봇, 동적 카드 갱신(User-Specific Views) |
| **Outgoing Webhook** | 표준 채널 (`@멘션` 호출) | Adaptive Card, Hero, Thumbnail 등 (동기 HTTP 응답) | 채널에서 `@웹훅이름` 멘션 시 백엔드로 HTTP POST 전송, 5초 이내 응답 반환 | 사내 단순 질의응답, 커스텀 슬래시 명령 대용 |
| **Microsoft Graph API** | 채널(비공개 포함), 그룹 채팅 | HTML 본문, 카드 첨부(`attachments`), `@mention` | Change Notifications(`POST /subscriptions`) 구독을 통한 실시간 메시지 수신 | 엔터프라이즈 백엔드 직접 연동, 마이그레이션/관리 자동화 |

---

## 2. 문서 목록 (파일별 구성)

| 번호 | 파일명 | 주요 내용 |
| :---: | :--- | :--- |
| **01** | [01_backend_message_and_webhook_json.md](./01_backend_message_and_webhook_json.md) | **백엔드 송·수신 공통 JSON 프로토콜**: Workflows Webhook 요청 JSON, Bot Framework Activity(수신/송신/Proactive), Microsoft Graph API `chatMessage` JSON, Outgoing Webhook 요청/응답 JSON, 크기·Rate Limit 제한 |
| **02** | [02_card_types_and_action_cards_json.md](./02_card_types_and_action_cards_json.md) | **알림 카드 및 액션 카드 JSON 레퍼런스**: `MessageCard`(O365 Connector ActionCard), `Hero Card`, `Thumbnail Card`, `List Card`, `Receipt Card`, `Sign-in Card`, `Carousel/List Collection` 전체 JSON 예시 및 제약사항 |
| **03** | [03_adaptive_cards_and_backend_handling_json.md](./03_adaptive_cards_and_backend_handling_json.md) | **Adaptive Card JSON 및 백엔드 액션 처리**: 입력 폼·레이아웃·Teams 전용 확장(`msteams` 폭 확장, `@mention`), `Action.Submit` vs `Action.Execute`(Universal Action Model), `adaptiveCard/action` Invoke 요청/응답 JSON, User-Specific Views(`refresh`) |
| **04** | [04_workflows_webhook_and_messaging_guide.md](./04_workflows_webhook_and_messaging_guide.md) | **Workflows 기반 채널/그룹 채팅 메시지 구현**: Webhook 수신 트리거 설정·보안·`Parse JSON` 매핑, 카드 전송 및 양방향 응답 대기(`wait for a response` vs 분리형 리스너), 실무 주의사항(Gotchas) |
| **05** | [05_workflows_create_channel_and_group_chat.md](./05_workflows_create_channel_and_group_chat.md) | **Workflows로 채널·그룹 채팅 직접 생성 및 연동**: `Create a chat`(그룹 채팅 생성), `Create a channel`(표준/비공개/공유 채널 생성), 멤버 추가, 동적 `Conversation ID` / `Channel ID` 바인딩, 비공개 채널 우회 전략 및 E2E 실전 시나리오 |
| **06** | [06_workflows_ui_and_expressions_step_by_step.md](./06_workflows_ui_and_expressions_step_by_step.md) | **Workflows UI 클릭 순서 · 흐름 편집기 · 수식(`fx`) 완전 정복**: 채팅방에서 워크플로 선택·생성, `Send each adaptive card` 제거 및 `Parse JSON` 조립, 새/클래식 디자이너, 동적 콘텐츠(`⚡`) vs 수식(`fx`) 치트시트, 실습 A/B 및 디버깅 팁 |
| **07** | [07_billing_teams_java_api_guide.md](./07_billing_teams_java_api_guide.md) | **MS Teams 알림·서명 링크 라이브러리 및 빌링 데모 구현 가이드**: [`teams-notify/`](./teams-notify) 멀티 모듈 프로젝트 (`teams-core` / `teams-spring-boot-starter` / `teams-demo-app`) — `TeamsNotifier` 라이브러리, `HMAC-SHA256` 서명 링크 검증 화면(`/ops/transactions/{txId}`), 6종 포맷 비교 |

---

## 3. 빠른 선택 가이드

- **단순 단방향 알림 (서버 → Teams 채널/그룹 채팅)**: `01` + `04` (Workflows Incoming Webhook + Adaptive Card JSON)
- **Teams UI 클릭부터 `Parse JSON`·수식(`fx`) 작성법까지 처음부터 따라하기**: `06` (채팅에서 시작하기 · 흐름 편집기 · 수식 백과사전)
- **Java 백엔드 라이브러리(`TeamsNotifier`)로 알림 발송 + 링크 클릭 Teams 유입 검증까지 바로 실행**: `07` + [`teams-notify/`](./teams-notify)
- **승인 요청/설문처럼 버튼 클릭 후 백엔드/플로우에서 후속 처리**:
  - 로우코드(Power Automate) 중심: `03` + `04` + `06` (`Post adaptive card and wait for a response`)
  - 커스텀 백엔드 서버 중심: `01` + `03` (Teams Bot + `Action.Execute` / `adaptiveCard/action`)
- **레거시 `MessageCard` JSON을 보유한 상태에서의 이전**: `02` + `04` (렌더링 호환 확인 후 Adaptive Card로 전환 권장)
- **이벤트 발생 시 새 채널이나 새 그룹 채팅방 자체를 자동 개설**: `05` (`Create a chat` / `Create a channel` + 카드 전송)
