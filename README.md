# MS Teams 메시지·카드 백엔드 JSON 형식 및 Java 알림 라이브러리

Microsoft Teams의 **채널(Channel)** 및 **그룹 채팅(Group Chat)** 을 대상으로 알림, 액션 카드, Adaptive Card를 송수신·처리하기 위한 **백엔드 JSON 스펙 · Workflows 가이드(`docs/`)**와, 다른 서비스에서 바로 가져다 쓸 수 있는 **범용 Java 17 / Spring Boot Starter 알림·서명 링크 라이브러리(`teams-notify/`)** 저장소입니다.

---

## 1. 디렉터리 구조

```text
ms-teams-guide/
├── docs/                                            # MS Teams 백엔드 JSON 스펙 & Workflows UI·수식 가이드 (01~06)
└── teams-notify/                                    # Java 17 / Spring Boot 3 공용 라이브러리 & 빌링 데모 프로젝트
    ├── README.md                                    # 라이브러리 연동 가이드 · 실행 방법 · REST API 명세
    ├── docs/INTELLIJ_RUN_GUIDE.md                   # IntelliJ IDEA 데모 앱 실행 및 환경변수 설정 가이드
    ├── docs/CODING_RULES.md                         # 공통 라이브러리 8대 코딩 규칙 및 체크리스트
    ├── teams-core/                                  # 순수 Java 17 라이브러리 (Spring·도메인 의존성 0개)
    ├── teams-spring-boot-starter/                   # Spring Boot 3 AutoConfiguration 스타터
    └── teams-demo-app/                              # 빌링 시나리오 테스트용 REST API & 검증 웹 콘솔
```

---

## 2. 아키텍처 개요 및 연동 방식 비교

> [!IMPORTANT]
> **Office 365 Connectors(레거시 Incoming Webhook) 은퇴 안내**
> Microsoft Teams의 기존 Office 365 Connectors(레거시 커넥터 기반 Incoming Webhook)는 **2026년 5월 완전히 은퇴(Retired)** 되었습니다.
> 현재는 **Teams Workflows 앱(Power Automate 기반)** 의 `When a Teams webhook request is received` 트리거 또는 **Teams Bot(Teams SDK / Bot Framework)**, **Microsoft Graph API**를 사용해야 합니다.

| 연동 방식 | 전송 대상 | 카드 지원 범위 | 양방향(사용자 응답) 처리 | 주요 용도 |
| :--- | :--- | :--- | :--- | :--- |
| **Workflows Incoming Webhook** | 채널, 그룹 채팅, 1:1 채팅 | Adaptive Card(최대 v1.6), MessageCard(렌더링만) | 웹훅 단독 불가 → Workflows 내 `Post adaptive card and wait for a response` 또는 `When someone responds...` 조합 필요 | CI/CD, 모니터링 알림, 외부 시스템 Webhook 연동 |
| **Workflows (Power Automate 커넥터)** | 표준/공유 채널, 그룹 채팅, 1:1 채팅 | 텍스트/HTML, Adaptive Card | `Post adaptive card and wait for a response`, `When someone responds to an adaptive card`, `For a selected message (V2)` | 노코드/로우코드 승인 플로우, **동적 채널/그룹 채팅 생성** 후 알림 |
| **Teams Bot (Bot Framework / Teams SDK)** | 채널(스레드 포함), 그룹 채팅, 1:1 채팅 | 모든 카드(Adaptive, Hero, Thumbnail, List, Receipt, Sign-in, Carousel, O365 Connector) | `Action.Execute` (`invoke` / `adaptiveCard/action`), `Action.Submit`, `messageBack`, `imBack`, `task/fetch` | 맞춤형 인터랙티브 챗봇, 동적 카드 갱신(User-Specific Views) |
| **Outgoing Webhook** | 표준 채널 (`@멘션` 호출) | Adaptive Card, Hero, Thumbnail 등 (동기 HTTP 응답) | 채널에서 `@웹훅이름` 멘션 시 백엔드로 HTTP POST 전송, 5초 이내 응답 반환 | 사내 단순 질의응답, 커스텀 슬래시 명령 대용 |
| **Microsoft Graph API** | 채널(비공개 포함), 그룹 채팅 | HTML 본문, 카드 첨부(`attachments`), `@mention` | Change Notifications(`POST /subscriptions`) 구독을 통한 실시간 메시지 수신 | 엔터프라이즈 백엔드 직접 연동, 마이그레이션/관리 자동화 |

---

## 3. 문서 및 프로젝트 안내

### 3.1 MS Teams 공통 스펙 · Workflows 가이드 (`docs/`)

| 번호 | 문서 | 주요 내용 |
| :---: | :--- | :--- |
| **01** | [01_backend_message_and_webhook_json.md](./docs/01_backend_message_and_webhook_json.md) | **백엔드 송·수신 공통 JSON 프로토콜**: Workflows Webhook 요청 JSON, Bot Framework Activity, Microsoft Graph API `chatMessage` JSON, Outgoing Webhook 요청/응답 JSON, 크기·Rate Limit 제한 |
| **02** | [02_card_types_and_action_cards_json.md](./docs/02_card_types_and_action_cards_json.md) | **알림 카드 및 액션 카드 JSON 레퍼런스**: `MessageCard`, `Hero Card`, `Thumbnail Card`, `List Card`, `Receipt Card`, `Sign-in Card`, `Carousel` 전체 JSON 예시 및 제약사항 |
| **03** | [03_adaptive_cards_and_backend_handling_json.md](./docs/03_adaptive_cards_and_backend_handling_json.md) | **Adaptive Card JSON 및 백엔드 액션 처리**: 입력 폼·레이아웃·Teams 전용 확장(`msteams` 폭 확장, `@mention`), `Action.Submit` vs `Action.Execute`, User-Specific Views(`refresh`) |
| **04** | [04_workflows_webhook_and_messaging_guide.md](./docs/04_workflows_webhook_and_messaging_guide.md) | **Workflows 기반 채널/그룹 채팅 메시지 구현**: Webhook 수신 트리거 설정·보안·`Parse JSON` 매핑, 카드 전송 및 양방향 응답 대기, 실무 주의사항(Gotchas) |
| **05** | [05_workflows_create_channel_and_group_chat.md](./docs/05_workflows_create_channel_and_group_chat.md) | **Workflows로 채널·그룹 채팅 직접 생성 및 연동**: `Create a chat`, `Create a channel`, 멤버 추가, 동적 `Conversation ID` / `Channel ID` 바인딩 및 E2E 시나리오 |
| **06** | [06_workflows_ui_and_expressions_step_by_step.md](./docs/06_workflows_ui_and_expressions_step_by_step.md) | **Workflows UI 클릭 순서 · 흐름 편집기 · 수식(`fx`) 완전 정복**: 채팅방에서 워크플로 생성, `Send each adaptive card` 제거 및 `Parse JSON` 조립, 동적 콘텐츠(`⚡`) vs 수식(`fx`) 치트시트 |

### 3.2 Java / Spring Boot 알림 라이브러리 프로젝트 (`teams-notify/`)

| 문서 | 주요 내용 |
| :--- | :--- |
| **[teams-notify/README.md](./teams-notify/README.md)** | **`TeamsNotifier` 라이브러리 연동 & 빌링 운영 데모 가이드**: 멀티 모듈 구조(`teams-core` / `teams-spring-boot-starter` / `teams-demo-app`), 외부 프로젝트 연동 예시, `HMAC-SHA256` 서명 링크 유입 검증 화면, 6종 알림 포맷 비교 API |
| **[teams-notify/docs/INTELLIJ_RUN_GUIDE.md](./teams-notify/docs/INTELLIJ_RUN_GUIDE.md)** | **IntelliJ IDEA 데모 앱 실행 가이드**: 하위 `pom.xml` Maven 프로젝트 등록, JDK 17 확인, Run Configuration 환경변수(`TEAMS_WEBHOOK_URL` 등) 설정 및 트러블슈팅 |
| **[teams-notify/docs/CODING_RULES.md](./teams-notify/docs/CODING_RULES.md)** | **공통 라이브러리 8대 코딩 규칙**: 도메인 중립 네이밍, 3-계층 모듈 경계 및 무의존성 원칙, 불변 API·Builder 설계, `@AutoConfiguration` 에티켓, 보안 위생, `package-info.java` 및 `Xdoclint` 무경고 Javadoc 규칙 |

---

## 4. 빠른 선택 가이드

- **단순 단방향 알림 (서버 → Teams 채널/그룹 채팅)**: [`01`](./docs/01_backend_message_and_webhook_json.md) + [`04`](./docs/04_workflows_webhook_and_messaging_guide.md)
- **Teams UI 클릭부터 `Parse JSON`·수식(`fx`) 작성법까지 처음부터 따라하기**: [`06`](./docs/06_workflows_ui_and_expressions_step_by_step.md)
- **Java / Spring Boot 프로젝트에 라이브러리(`TeamsNotifier`)로 붙이거나 서명 링크 유입 검증 실행하기**: [`teams-notify/README.md`](./teams-notify/README.md)
- **IntelliJ IDEA에서 `teams-demo-app` 바로 띄우기**: [`teams-notify/docs/INTELLIJ_RUN_GUIDE.md`](./teams-notify/docs/INTELLIJ_RUN_GUIDE.md)
- **라이브러리 기여 및 아키텍처·코딩 표준 확인하기**: [`teams-notify/docs/CODING_RULES.md`](./teams-notify/docs/CODING_RULES.md)
