# 05. Workflows로 채널·그룹 채팅 생성 및 알림·카드 연동하기

Microsoft Teams Workflows(Power Automate)의 **Microsoft Teams 커넥터**에는 기존 채팅방이나 채널에 글을 쓰는 기능뿐만 아니라, **새로운 그룹 채팅방(`Create a chat`)이나 새로운 팀 채널(`Create a channel`)을 동적으로 생성**하고 멤버를 초대한 뒤 곧바로 Adaptive Card나 알림 메시지를 게시하는 액션이 내장되어 있습니다.

---

## 1. 새 그룹 채팅 생성 및 카드/메시지 전송 (`Create a chat`)

인시던트 대응 워룸(War Room), 특정 고객 문의방, 승인 논의방처럼 **사건/이벤트가 발생할 때마다 해당 멤버들만 모인 새 그룹 채팅방을 자동으로 개설**하고 첫 메시지를 보낼 때 사용합니다.

### 1.1 `Create a chat` 액션 파라미터 및 출력

| 항목 | 파라미터 / 출력 | 설명 |
| :--- | :--- | :--- |
| **입력** | `Members to add` (`members`, 필수) | 초대할 사용자의 이메일/UPN 또는 Entra ID 목록. **세미콜론(`;`)으로 구분** (예: `kim@contoso.com;lee@contoso.com;park@contoso.com`) |
| **입력** | `Title` (`topic`, 선택) | 그룹 채팅방 상단에 표시될 채팅방 제목 (예: `[P1 장애] 결제 API 지연 대응방`) |
| **출력** | `Conversation ID` (`id`) | 생성된 채팅방의 고유 ID (예: `19:8b3f...@thread.v2`). 후속 메시지/카드 액션에 사용 |

> [!WARNING]
> **그룹 채팅 생성 시 제한사항**
> - 한 번에 최대 **20명**까지 추가할 수 있습니다.
> - **게스트(Guest) 및 외부 사용자는 지원하지 않습니다.** (동일 테넌트 M365 사용자만 가능)
> - 동일 구성원과 같은 조건이더라도 호출 시 새로운 채팅 컨텍스트가 열리거나 1:1 채팅(2명)으로 생성될 수 있습니다. 그룹 채팅방 제목(`Title`)은 3명 이상일 때 표시됩니다.

### 1.2 생성한 그룹 채팅에 Adaptive Card / 메시지 연결하는 방법

Teams 커넥터의 메시지/카드 전송 드롭다운은 **기본적으로 최근 이름 있는 그룹 채팅 50개만 표시**합니다. 방금 `Create a chat`으로 만든 새 채팅방에 글을 쓰려면 **동적 ID 바인딩**이 필수입니다.

1. **`Create a chat`** 액션 추가 후 `Members to add`와 `Title` 설정.
2. 다음 단계로 **`Post message in a chat or channel`**, **`Post card in a chat or channel`**, 또는 **`Post adaptive card and wait for a response`** 추가.
3. 다음과 같이 설정:
   - **Post as**: `Flow bot` 또는 `User`
   - **Post in**: `Group chat`
   - **Group chat**: 드롭다운 목록 맨 아래로 스크롤하여 **`Enter custom value` (사용자 지정 값 입력)** 선택
   - 동적 콘텐츠(번개 아이콘 ⚡)에서 `Create a chat` 결과의 **`Conversation ID`** (수식: `@{outputs('Create_a_chat')?['body/id']}`) 선택.
4. 본문(`Message`) 또는 `Adaptive Card` JSON 작성 후 저장.

### 1.3 기존 그룹 채팅 멤버 관리 액션
- **`Add a user to a chat`**: 생성 후 추가 인원 투입 시 사용 (`chatId`, `userId`, `owner` 여부, `visibleHistoryStartDateTime` 이전 대화 내역 공개 시작 시점 지정 가능).
- **`Remove a member from a chat`**: 그룹 채팅에서 특정 멤버 제외.

---

## 2. 새 팀 채널 생성 및 멤버 초대·카드 전송 (`Create a channel`)

신규 프로젝트 온보딩, 부서별 업무 공간 개설, 특정 고객사/릴리즈 전용 채널을 자동으로 만들고 초기 안내 카드를 게시하는 구성입니다.

### 2.1 `Create a channel` 액션 파라미터 및 출력

| 항목 | 파라미터 / 출력 | 설명 |
| :--- | :--- | :--- |
| **입력** | `Team` (`groupId`, 필수) | 채널을 생성할 대상 팀 선택 (또는 동적 Team GUID) |
| **입력** | `Name` (`displayName`, 필수) | Teams에 표시될 채널 이름 (예: `proj-2026-apollo`) |
| **입력** | `Description` (`description`, 선택) | 채널 설명 텍스트 |
| **입력** | `Membership Type` (`membershipType`, 선택) | **`standard`** (표준, 기본값), **`private`** (비공개), **`shared`** (공유) |
| **출력** | `Channel ID` (`id`) | 생성된 채널의 고유 ID (예: `19:abc123...@thread.tacv2`) |

### 2.2 비공개/공유 채널 멤버 추가 (`Add a member to a channel`)
`Membership Type`을 `private` 또는 `shared`로 생성한 경우, 팀 전체 멤버에게 보이지 않으므로 후속 액션을 통해 참여자를 초대해야 합니다.
- **Team** (`groupId`): 동일 팀 ID
- **Channel** (`channelId`): `Create a channel` 출력의 `Channel ID`
- **User** (`userId`): 사용자 UPN 또는 Entra Object ID
- **Set user as channel owner** (`owner`): 소유자 지정 시 `true`

### 2.3 생성한 채널에 메시지/Adaptive Card 게시 시 중요 주의사항

1. **표준(Standard) / 공유(Shared) 채널인 경우**:
   - 후속 액션으로 `Post message in a chat or channel` 또는 `Post card in a chat or channel` / `Post adaptive card and wait for a response`를 추가합니다.
   - **Post in**: `Channel`
   - **Team**: 대상 팀 선택
   - **Channel**: `Enter custom value` 선택 → `Create a channel`의 **`Channel ID`** (`@{outputs('Create_a_channel')?['body/id']}`) 매핑.
2. **비공개(Private) 채널인 경우의 제약과 우회 방법**:
   - **제약**: Power Automate Teams 커넥터의 `Post message/card in a chat or channel` 액션은 **비공개 채널에 대한 메시지/카드 포스팅을 지원하지 않습니다.**
   - **대안 1 (Graph API 액션 활용)**: Teams 커넥터 내 내장된 **`Send a Microsoft Graph HTTP request`** 액션을 사용하면 비공개 채널에도 메시지를 보낼 수 있습니다.
     - Method: `POST`
     - URI: `https://graph.microsoft.com/v1.0/teams/{team-id}/channels/{channel-id}/messages`
     - Body: [01번 문서의 Graph API `chatMessage` JSON](./01_backend_message_and_webhook_json.md#31-채널-또는-그룹-채팅에-카드-메시지-전송)
   - **대안 2 (Workflows Webhook)**: 채널 생성 후 해당 비공개 채널에서 Workflows Webhook을 구성하여 호출.

> [!TIP]
> 새 채널 생성 직후(`Create a channel`) 바로 메시지를 보내면 Teams 백엔드 프로비저닝 지연으로 간헐적 404 오류가 날 수 있습니다. 안정적인 운영을 위해 **`Delay` (예: 5~10초)** 액션을 사이에 두는 것을 권장합니다.

---

## 3. 팀 생성, 태그 관리 및 Graph HTTP 확장 액션

Workflows의 Teams 커넥터에서는 채널·채팅 생성 외에도 다음 관리 액션을 조합할 수 있습니다.

- **`Create a team`**: 새 팀 자체를 생성 (`displayName`, `description`, `visibility`: `Public` | `Private`) → `Add a member to a team`으로 팀원 추가 → `Create a channel`로 하위 채널 구성.
- **`Create a tag for a team` / `Add a member to a team tag`**: 장애 대응용 `@OnCall` 같은 팀 태그를 동적으로 만들고 담당자를 배정한 뒤, `Get an @mention token for a team tag`로 채널 카드에서 태그 전체 멘션.
- **`Archive a channel`**: 프로젝트 종료 시 채널을 읽기 전용으로 보관(비동기 작업).
- **`Send a Microsoft Graph HTTP request`**: 커넥터 기본 UI에 없는 세부 Graph API 요청(`/teams`, `/me`, `/users` 하위 `channels`, `chats`, `messages`, `pinnedMessages`, `onlineMeetings`)을 추가 인증 설정 없이 바로 실행.

---

## 4. 실전 엔드투엔드(E2E) Workflows 구현 시나리오

### 시나리오 1: 장애 Webhook 수신 → 워룸(그룹 채팅) 자동 개설 → 대응 카드 처리

**목적**: 모니터링 시스템이 심각도 `CRITICAL` Webhook을 보내면, 담당자 3명이 모인 그룹 채팅방을 즉시 생성하고, 대응 선택용 Adaptive Card를 보낸 뒤, 담당자의 응답을 백엔드 API로 전달합니다.

```text
[1. Trigger] When a Teams webhook request is received (백엔드 알림 JSON 수신)
      ↓
[2. Action] Data Operation - Parse JSON (service, severity, oncallEmails[], incidentId 추출)
      ↓
[3. Action] Microsoft Teams - Create a chat
    • Members to add: @{join(body('Parse_JSON')?['oncallEmails'], ';')}
    • Title: [장애대응] @{body('Parse_JSON')?['incidentId']} - @{body('Parse_JSON')?['service']}
      ↓
[4. Action] Microsoft Teams - Get an @mention token for a user (대표 담당자 멘션 토큰 생성)
      ↓
[5. Action] Microsoft Teams - Post adaptive card and wait for a response
    • Post as: Flow bot
    • Post in: Group chat -> Enter custom value -> @{outputs('Create_a_chat')?['body/id']}
    • Update message: 대응 상태가 백엔드에 기록되었습니다.
    • Message (Adaptive Card JSON): 아래 JSON 참고
      ↓
[6. Action] HTTP (백엔드 티켓 서버로 POST: responder, submitActionId, 코멘트 전송)
```

#### 5단계 `Message` 필드에 넣는 Adaptive Card JSON (동적 표현식 포함)
```json
{
  "$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
  "type": "AdaptiveCard",
  "version": "1.4",
  "body": [
    {
      "type": "TextBlock",
      "text": "🚨 긴급 장애 대응 워룸이 개설되었습니다",
      "weight": "Bolder",
      "size": "Medium",
      "color": "Attention"
    },
    {
      "type": "TextBlock",
      "text": "담당자 @{outputs('Get_an_@mention_token_for_a_user')?['body/atMention']} 확인 바랍니다.",
      "wrap": true
    },
    {
      "type": "FactSet",
      "facts": [
        { "title": "인시던트 ID", "value": "@{body('Parse_JSON')?['incidentId']}" },
        { "title": "서비스", "value": "@{body('Parse_JSON')?['service']}" }
      ]
    },
    {
      "type": "Input.Text",
      "id": "actionNote",
      "label": "조치 내용 / 코멘트",
      "isMultiline": true
    }
  ],
  "actions": [
    {
      "type": "Action.Submit",
      "id": "takeOwnership",
      "title": "담당 수락 (Acknowledge)"
    },
    {
      "type": "Action.Submit",
      "id": "escalate",
      "title": "상위 에스컬레이션"
    }
  ]
}
```

---

### 시나리오 2: 신규 프로젝트 요청 승인 → 전용 팀 채널 자동 생성 → 온보딩 카드 게시

**목적**: 백엔드/폼에서 프로젝트 개설 요청이 들어오면 팀 내에 표준 채널을 만들고, 프로젝트 정보와 링크 버튼이 담긴 환영 카드를 게시합니다.

```text
[1. Trigger] When an HTTP request is received (또는 SharePoint / Webhook 트리거)
      ↓
[2. Action] Microsoft Teams - Create a channel
    • Team: <대상 Team 선택>
    • Name: @{triggerBody()?['projectCode']}
    • Description: @{triggerBody()?['projectSummary']}
    • Membership Type: standard
      ↓
[3. Action] Schedule - Delay (10 Seconds - 채널 프로비저닝 대기)
      ↓
[4. Action] Microsoft Teams - Post card in a chat or channel
    • Post as: Flow bot
    • Post in: Channel
    • Team: <동일 Team 선택>
    • Channel: Enter custom value -> @{outputs('Create_a_channel')?['body/id']}
    • Adaptive Card: 프로젝트 저장소, Jira 보드, Wiki 바로가기 Action.OpenUrl 포함 카드
```
