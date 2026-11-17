# 01. 백엔드 메시지·웹훅 송수신 공통 JSON 형식

백엔드가 Microsoft Teams의 **채널(Channel)** 또는 **그룹 채팅(Group Chat)** 으로 메시지를 보내거나, 채널/채팅에서 발생한 메시지·액션 이벤트를 수신하여 처리할 때 사용하는 4가지 핵심 프로토콜의 JSON 형식을 정리합니다.

---

## 1. Workflows Incoming Webhook 요청 JSON (백엔드 → Teams)

Teams 채널 또는 그룹 채팅의 Workflows에서 `When a Teams webhook request is received` 트리거를 생성하면 HTTPS Webhook URL이 발급됩니다. 백엔드는 이 URL로 `POST` 요청을 보냅니다.

### 1.1 Adaptive Card 래퍼 형식 (권장)

Workflows Webhook이 표준으로 기대하는 최상위 JSON 래퍼입니다.

```json
{
  "type": "message",
  "attachments": [
    {
      "contentType": "application/vnd.microsoft.card.adaptive",
      "contentUrl": null,
      "content": {
        "$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
        "type": "AdaptiveCard",
        "version": "1.4",
        "body": [
          {
            "type": "TextBlock",
            "text": "배포 알림",
            "weight": "Bolder",
            "size": "Medium"
          },
          {
            "type": "TextBlock",
            "text": "Production 서버 배포가 완료되었습니다.",
            "wrap": true
          }
        ]
      }
    }
  ]
}
```

- **`type`**: 반드시 `"message"` 여야 합니다.
- **`attachments`**: 1개 이상의 카드 객체 배열입니다. Workflows 내부의 `Send Each Adaptive` 루프가 이 배열을 순회하며 카드를 게시합니다.
- **`contentType`**: Adaptive Card의 경우 `"application/vnd.microsoft.card.adaptive"` 고정입니다.
- **`contentUrl`**: `null`로 지정합니다.
- **`content`**: 실제 Adaptive Card JSON 본문입니다.

### 1.2 레거시 MessageCard 직접 전송 형식

기존 Office 365 Connector 호환성을 위해 단일 `MessageCard` 객체도 직접 수신할 수 있습니다.

```json
{
  "@type": "MessageCard",
  "@context": "http://schema.org/extensions",
  "themeColor": "0076D7",
  "summary": "빌드 실패 알림",
  "title": "CI/CD Pipeline #482 Failed",
  "text": "main 브랜치 빌드 중 테스트 2건이 실패했습니다."
}
```

> [!WARNING]
> Workflows Webhook은 `MessageCard`의 **콘텐츠 렌더링**은 지원하지만 `HttpPOST` 액션 버튼(외부 URL로 직접 POST)은 동작하지 않습니다. 인터랙티브 버튼이 필요하면 Adaptive Card와 Workflows 응답 처리를 사용해야 합니다.

### 1.3 단순 텍스트 또는 커스텀 JSON

Workflows에서 `Parse JSON` 액션을 직접 구성한 경우, 아래와 같은 임의의 백엔드 커스텀 JSON을 보내고 Workflow 내부에서 카드로 조립할 수도 있습니다.

```json
{
  "service": "payment-api",
  "severity": "CRITICAL",
  "message": "DB Connection Pool 고갈 (98% 사용 중)",
  "timestamp": "2026-11-17T14:00:00Z",
  "dashboardUrl": "https://monitoring.example.com/db"
}
```

### 1.4 Webhook 전송 제약사항 및 Rate Limit

- **메시지 크기 제한**: 최대 **28 KB** (초과 시 `413 Request Entity Too Large` 오류)
- **Rate Limit (초당/시간당 허용량)**:
  - 1초: 최대 4회
  - 30초: 최대 60회
  - 1시간(3,600초): 최대 100회
  - 2시간(7,200초): 최대 150회
  - 24시간(86,400초): 최대 1,800회
- **HTTP 429 처리**: 응답 본문에 `"Microsoft Teams endpoint returned HTTP error 429"`가 포함될 수 있으므로 지수 백오프(Exponential Backoff) 재시도 로직을 구현해야 합니다.

---

## 2. Bot Framework / Teams SDK Activity JSON (양방향 백엔드 봇)

커스텀 백엔드 서버(`/api/messages`)가 Teams의 채널 또는 그룹 채팅에서 메시지를 **수신**하고 처리한 뒤 회신하거나, 능동적으로 **선제 메시지(Proactive Message)** 를 보낼 때 사용하는 표준 **Activity JSON** 스키마입니다.

### 2.1 백엔드가 수신하는 메시지 Activity JSON (채널 & 그룹 채팅)

사용자가 채널에서 `@봇`을 멘션하거나, 그룹 채팅에서 `@봇`을 멘션하거나, 카드 버튼을 누르면 Teams가 백엔드 엔드포인트로 아래 JSON을 `POST`합니다.

#### ① 채널에서 수신한 메시지 예시 (`conversationType: "channel"`)

```json
{
  "type": "message",
  "id": "1700000000001",
  "timestamp": "2026-11-17T14:05:00.000Z",
  "serviceUrl": "https://smba.trafficmanager.net/kr/",
  "channelId": "msteams",
  "from": {
    "id": "29:1AbCdEfGhIjKlMnOpQrStUvWxYz",
    "name": "홍길동",
    "aadObjectId": "87d349ed-44d7-43e1-9a83-5f2406dee5bd"
  },
  "conversation": {
    "isGroup": true,
    "conversationType": "channel",
    "tenantId": "2432b57b-0abd-43db-aa7b-16eadd115d34",
    "id": "19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2;messageid=1700000000000"
  },
  "recipient": {
    "id": "28:11111111-2222-3333-4444-555555555555",
    "name": "MyAlertBot"
  },
  "text": "<at>MyAlertBot</at> 서버 상태 확인해줘",
  "textFormat": "plain",
  "entities": [
    {
      "type": "mention",
      "text": "<at>MyAlertBot</at>",
      "mentioned": {
        "id": "28:11111111-2222-3333-4444-555555555555",
        "name": "MyAlertBot"
      }
    }
  ],
  "channelData": {
    "channel": {
      "id": "19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2"
    },
    "team": {
      "id": "19:teamIdExample@thread.tacv2"
    },
    "tenant": {
      "id": "2432b57b-0abd-43db-aa7b-16eadd115d34"
    }
  }
}
```

#### ② 그룹 채팅에서 수신한 메시지 예시 (`conversationType: "groupChat"`)

```json
{
  "type": "message",
  "id": "1700000000099",
  "timestamp": "2026-11-17T14:06:00.000Z",
  "serviceUrl": "https://smba.trafficmanager.net/kr/",
  "channelId": "msteams",
  "from": {
    "id": "29:1AbCdEfGhIjKlMnOpQrStUvWxYz",
    "name": "홍길동",
    "aadObjectId": "87d349ed-44d7-43e1-9a83-5f2406dee5bd"
  },
  "conversation": {
    "isGroup": true,
    "conversationType": "groupChat",
    "tenantId": "2432b57b-0abd-43db-aa7b-16eadd115d34",
    "id": "19:groupChatUuid12345@thread.v2"
  },
  "recipient": {
    "id": "28:11111111-2222-3333-4444-555555555555",
    "name": "MyAlertBot"
  },
  "text": "<at>MyAlertBot</at> 장애 티켓 생성",
  "channelData": {
    "tenant": {
      "id": "2432b57b-0abd-43db-aa7b-16eadd115d34"
    }
  }
}
```

#### 백엔드 파싱 시 핵심 필드 설명

| 필드 경로 | 설명 |
| :--- | :--- |
| `serviceUrl` | 봇이 답장을 보낼 대상 엔드포인트 베이스 URL (리전별 상이: `amer`, `emea`, `apac`, `kr` 등). 반드시 저장해 두어야 Proactive 메시지 전송 가능 |
| `conversation.conversationType` | `"channel"` (팀 채널), `"groupChat"` (그룹 채팅), `"personal"` (1:1 채팅) 구분 |
| `conversation.id` | 채널 스레드의 경우 `19:xxx@thread.tacv2;messageid=1700000000000` 형태이며, 그룹 채팅은 `19:xxx@thread.v2` 형태 |
| `from.aadObjectId` | 메시지를 보낸 사용자의 Microsoft Entra ID(구 Azure AD) Object ID |
| `channelData.team.id` / `channelData.channel.id` | 채널 메시지일 때만 포함됨 (`channelData.teamsChannelId`는 구버전 하위 호환용으로 Deprecated) |
| `replyToId` | 채널 스레드 답글이라도 Teams가 생략할 수 있으므로, `conversation.id` 내 `messageid=<rootId>`와 현재 `activity.id`를 비교해 루트 글/답글 여부를 판단하는 것이 안전함 |

### 2.2 백엔드가 채널에 새 스레드(Proactive Message) 생성 시 송신 JSON

기존 대화에 답글을 달지 않고, 백엔드가 채널에 **새로운 루트 게시물(카드 포함)** 을 작성하려면 `{serviceUrl}/v3/conversations`로 `POST` 요청을 보냅니다. (Authorization: Bot Framework OAuth Bearer Token)

```json
{
  "bot": {
    "id": "28:11111111-2222-3333-4444-555555555555",
    "name": "MyAlertBot"
  },
  "isGroup": true,
  "channelData": {
    "channel": {
      "id": "19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2"
    },
    "tenant": {
      "id": "2432b57b-0abd-43db-aa7b-16eadd115d34"
    }
  },
  "activity": {
    "type": "message",
    "text": "새 장애 알림이 등록되었습니다.",
    "attachments": [
      {
        "contentType": "application/vnd.microsoft.card.adaptive",
        "content": {
          "$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
          "type": "AdaptiveCard",
          "version": "1.5",
          "body": [
            {
              "type": "TextBlock",
              "text": "Incident #9021",
              "weight": "Bolder"
            }
          ]
        }
      }
    ]
  }
}
```

> [!NOTE]
> **기존 그룹 채팅 또는 채널 스레드에 답글/후속 메시지 전송**:
> `POST {serviceUrl}/v3/conversations/{conversationId}/activities` 엔드포인트에 `activity` JSON만 보내면 됩니다.
> 기존 메시지를 수정(카드 갱신 등)할 때는 `PUT {serviceUrl}/v3/conversations/{conversationId}/activities/{activityId}`를 사용합니다.
> Bot 메시지의 최대 크기는 **100 KB**입니다.

---

## 3. Microsoft Graph API `chatMessage` JSON

봇 앱 설치 없이(또는 위임된 사용자 권한/특정 Graph 권한으로) 백엔드가 직접 채널·채팅에 메시지를 쓰고 읽을 때 사용하는 JSON 형식입니다.

### 3.1 채널 또는 그룹 채팅에 카드 메시지 전송

- **채널 새 글**: `POST https://graph.microsoft.com/v1.0/teams/{team-id}/channels/{channel-id}/messages`
- **채널 스레드 답글**: `POST https://graph.microsoft.com/v1.0/teams/{team-id}/channels/{channel-id}/messages/{message-id}/replies`
- **그룹 채팅(또는 1:1 채팅)**: `POST https://graph.microsoft.com/v1.0/chats/{chat-id}/messages`

```json
{
  "subject": "시스템 점검 안내",
  "body": {
    "contentType": "html",
    "content": "안녕하세요 <at id=\"0\">김철수</at>님, 아래 카드를 확인해 주세요.<br/><attachment id=\"74d20c7f34aa4a7fb74e2b30004247c5\"></attachment>"
  },
  "attachments": [
    {
      "id": "74d20c7f34aa4a7fb74e2b30004247c5",
      "contentType": "application/vnd.microsoft.card.adaptive",
      "contentUrl": null,
      "content": "{\"$schema\":\"http://adaptivecards.io/schemas/adaptive-card.json\",\"type\":\"AdaptiveCard\",\"version\":\"1.4\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"정기 점검 일정: 2026-11-20 02:00 KST\",\"wrap\":true}],\"actions\":[{\"type\":\"Action.OpenUrl\",\"title\":\"공지 자세히 보기\",\"url\":\"https://status.example.com\"}]}",
      "name": null,
      "thumbnailUrl": null
    }
  ],
  "mentions": [
    {
      "id": 0,
      "mentionText": "김철수",
      "mentioned": {
        "user": {
          "displayName": "김철수",
          "id": "ef1c916a-3135-4417-ba27-8eb7bd084193",
          "userIdentityType": "aadUser"
        }
      }
    }
  ]
}
```

- **중요 규칙**:
  1. `attachments[].id`(임의 GUID)와 `body.content` 내부의 `<attachment id="..."></attachment>` 값이 반드시 일치해야 카드가 렌더링됩니다.
  2. `attachments[].content`는 JSON 객체가 아니라 **직렬화된 JSON 문자열(String)** 이어야 합니다.
  3. Graph API로 보낸 카드는 Bot이 연결되어 있지 않으므로 `Action.OpenUrl`만 동작하며, 인터랙티브 Submit/Execute 처리는 Bot 또는 Workflows가 필요합니다.

### 3.2 Graph API Change Notifications (메시지 수신 구독)

실시간으로 채널/채팅의 모든 메시지를 백엔드로 받아 처리하려면 Graph Subscription(`POST /subscriptions`)을 생성합니다.

- 구독 리소스: `/teams/{team-id}/channels/{channel-id}/messages` 또는 `/chats/{chat-id}/messages`
- 백엔드 Webhook 수신 JSON (알림 수신 후 `202 Accepted` 반환 및 `resource` 조회):

```json
{
  "value": [
    {
      "subscriptionId": "7f105c7d-2dc5-4530-97cd-4e7ae6534c07",
      "changeType": "created",
      "clientState": "SecretClientStateToken",
      "resource": "teams('fbe2bf47-16c8-47cf-b4a5-4b9b187c508b')/channels('19:4a95f7d8...@thread.tacv2')/messages('1700000000123')",
      "resourceData": {
        "@odata.type": "#Microsoft.Graph.chatMessage",
        "id": "1700000000123"
      }
    }
  ]
}
```

---

## 4. Outgoing Webhook 요청 및 응답 JSON (채널 전용 경량 양방향)

채널에서 `@웹훅이름 명령어` 형태로 입력했을 때 Teams가 지정된 백엔드 HTTPS URL로 동기 `POST` 요청을 보내고, 백엔드가 **5초 이내**에 카드 JSON을 반환하는 방식입니다. (그룹 채팅에서는 미지원)

### 4.1 Teams → 백엔드 요청 JSON

헤더에 `Authorization: HMAC <base64_hmac_sha256>`가 포함되며, 백엔드는 발급받은 Security Token으로 요청 본문 바이트의 HMAC-SHA256 서명을 검증해야 합니다.

```json
{
  "type": "message",
  "id": "1700000000555",
  "timestamp": "2026-11-17T14:10:00.000Z",
  "localTimestamp": "2026-11-17T23:10:00.000+09:00",
  "serviceUrl": "https://smba.trafficmanager.net/kr/",
  "channelId": "msteams",
  "from": {
    "id": "29:1AbCdEf...",
    "name": "홍길동",
    "aadObjectId": "87d349ed-44d7-43e1-9a83-5f2406dee5bd"
  },
  "conversation": {
    "isGroup": true,
    "id": "19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2;messageid=1700000000555",
    "conversationType": "channel",
    "tenantId": "2432b57b-0abd-43db-aa7b-16eadd115d34"
  },
  "text": "<at>DeployBot</at> status prod",
  "textFormat": "plain",
  "channelData": {
    "channel": { "id": "19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2" },
    "team": { "id": "19:teamIdExample@thread.tacv2" },
    "tenant": { "id": "2432b57b-0abd-43db-aa7b-16eadd115d34" }
  }
}
```

### 4.2 백엔드 → Teams 동기 응답 JSON (5초 타임아웃)

```json
{
  "type": "message",
  "attachments": [
    {
      "contentType": "application/vnd.microsoft.card.adaptive",
      "content": {
        "$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
        "type": "AdaptiveCard",
        "version": "1.4",
        "body": [
          {
            "type": "TextBlock",
            "text": "Production 상태: 정상 (v2.4.1)",
            "weight": "Bolder",
            "color": "Good"
          }
        ]
      }
    }
  ]
}
```
