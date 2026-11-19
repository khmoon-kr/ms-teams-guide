# 03. Adaptive Card JSON 및 백엔드 액션 처리 스펙

**Adaptive Card**는 Microsoft Teams(데스크톱, 웹, 모바일) 및 Workflows, Bot, Message Extension 전반에서 공식 권장되는 선언적 UI JSON 포맷입니다.

---

## 1. Adaptive Card 기본 구조 및 버전 가이드

- **지원 스키마 버전**: Teams 플랫폼 및 모바일 앱은 최대 **v1.6** 까지 지원합니다.
  - 단순 알림/Workflows 웹훅용: `1.2` ~ `1.4` 권장
  - Universal Actions(`Action.Execute`) 및 최신 입력 검증(`isRequired`, `errorMessage`) 사용 시: **`1.5`** 권장 (구버전 호환을 위해 `fallback` 지정 권장)
- **HTML 미지원**: Adaptive Card에서는 HTML 태그가 렌더링되지 않고 일반 텍스트로 출력됩니다. 서식은 **Markdown**만 사용해야 합니다.

### Markdown 및 줄바꿈 주의사항
- 지원 문법: `**굵게**`, `_기울임_`, `- 순서 없는 목록`, `1. 순서 있는 목록`, `[링크](https://...)`
- 미지원 문법: 제목(`#`), 표(Table), 이미지(`![]()`), 코드 블록, 인용구(`>`)
- **줄바꿈**: `TextBlock` 내 일반 문단 줄바꿈은 `\n\n`, 목록(`- Item 1\r- Item 2`)에서는 `\r` 또는 `\n`을 사용합니다.

---

## 2. 실전 입력 폼 & 레이아웃 Adaptive Card JSON 예시

장애/결재/설문 등 백엔드로 입력 데이터를 전송할 수 있는 실전 Adaptive Card 구조입니다.

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
        "version": "1.5",
        "msteams": {
          "width": "Full",
          "entities": [
            {
              "type": "mention",
              "text": "<at>김철수</at>",
              "mentioned": {
                "id": "chulsoo.kim@contoso.com",
                "name": "김철수"
              }
            }
          ]
        },
        "body": [
          {
            "type": "Container",
            "style": "emphasis",
            "items": [
              {
                "type": "TextBlock",
                "text": "🚨 프로덕션 배포 승인 요청",
                "weight": "Bolder",
                "size": "Large",
                "color": "Attention"
              },
              {
                "type": "TextBlock",
                "text": "담당자 <at>김철수</at>님의 승인이 필요합니다.",
                "wrap": true,
                "spacing": "Small"
              }
            ]
          },
          {
            "type": "FactSet",
            "facts": [
              { "title": "서비스", "value": "billing-api" },
              { "title": "버전", "value": "v3.12.0" },
              { "title": "요청자", "value": "이영희" }
            ]
          },
          {
            "type": "Input.ChoiceSet",
            "id": "deployWindow",
            "label": "배포 시간대 선택",
            "isRequired": true,
            "errorMessage": "배포 시간대를 선택해 주세요.",
            "style": "compact",
            "value": "immediate",
            "choices": [
              { "title": "즉시 배포", "value": "immediate" },
              { "title": "금일 23:00 KST", "value": "tonight_23" }
            ]
          },
          {
            "type": "Input.Text",
            "id": "reviewComment",
            "label": "승인/반려 사유",
            "placeholder": "코멘트를 입력하세요 (반려 시 필수)",
            "isMultiline": true
          },
          {
            "type": "Input.Toggle",
            "id": "notifyChannel",
            "title": "배포 완료 후 채널 공지 발송",
            "value": "true"
          }
        ],
        "actions": [
          {
            "type": "Action.Execute",
            "id": "btnApprove",
            "title": "승인",
            "verb": "approveDeploy",
            "associatedInputs": "auto",
            "data": {
              "deployId": "DEP-20261117-01",
              "decision": "approved"
            }
          },
          {
            "type": "Action.Execute",
            "id": "btnReject",
            "title": "반려",
            "verb": "rejectDeploy",
            "associatedInputs": "auto",
            "data": {
              "deployId": "DEP-20261117-01",
              "decision": "rejected"
            }
          },
          {
            "type": "Action.ShowCard",
            "title": "변경 내역 보기",
            "card": {
              "type": "AdaptiveCard",
              "body": [
                {
                  "type": "TextBlock",
                  "text": "- 결제 타임아웃 5s -> 8s 상향\r- Redis 연결 풀 크기 조정",
                  "wrap": true
                }
              ]
            }
          },
          {
            "type": "Action.OpenUrl",
            "title": "GitHub PR 열기",
            "url": "https://github.com/org/repo/pull/482"
          }
        ]
      }
    }
  ]
}
```

---

## 3. Teams 전용 확장 속성 (`msteams` & Component)

### 3.1 전체 너비 확장 (`msteams.width`)
기본적으로 Adaptive Card는 좁은 너비로 렌더링됩니다. `content` 루트에 아래 속성을 추가하면 채널/채팅 창 너비에 맞춰 확장됩니다.
```json
"msteams": {
  "width": "Full"
}
```
> [!TIP]
> 전체 너비 카드는 모바일 및 회의 사이드 패널 등 좁은 화면에서 잘리지 않도록 `ColumnSet`을 최대 3개 열 이하로 유지하고 고정 픽셀 너비 지정은 피하는 것이 좋습니다.

### 3.2 카드 내부 `@mention` (`msteams.entities`)
`TextBlock` 또는 `FactSet` 텍스트에 `<at>이름</at>`을 넣고, `msteams.entities` 배열에 매핑합니다.
- **지원 ID 유형 (Bot & Workflows Incoming Webhook 모두 지원)**:
  1. **UPN (이메일)**: `"id": "AdeleV@contoso.onmicrosoft.com"`
  2. **Microsoft Entra Object ID (GUID)**: `"id": "87d349ed-44d7-43e1-9a83-5f2406dee5bd"`
  3. **Teams User MRI (Bot 전용)**: `"id": "29:123124124124"`
- 참고: Incoming Webhook은 사용자 멘션만 지원하며 봇/채널/팀 멘션은 지원하지 않습니다.

### 3.3 Persona / Persona Set (사용자 프로필 아이콘)
```json
{
  "type": "Component",
  "name": "graph.microsoft.com/user",
  "view": "compact",
  "properties": {
    "id": "65f50003-e15d-434a-9e14-0fcfeb3d7817",
    "displayName": "김철수",
    "userPrincipalName": "chulsoo.kim@contoso.com"
  }
}
```
여러 사용자를 겹쳐 표시할 때는 `"name": "graph.microsoft.com/users"`, `"properties": { "users": [ ... ] }`를 사용합니다.

---

## 4. 백엔드 액션 처리 모델: `Action.Submit` vs `Action.Execute`

| 구분 | `Action.Submit` (레거시 / Workflows용) | `Action.Execute` (Universal Action Model, 봇 권장) |
| :--- | :--- | :--- |
| **주요 사용처** | **Workflows(`Post adaptive card and wait for a response` 등)**, 기존 Bot Framework 봇 | **Teams Bot (Teams SDK)**, Outlook Actionable Messages 겸용 |
| **전송 방식** | `message` Activity (또는 `msteams` 래퍼에 따라 `invoke`) | 항상 **`invoke`** Activity (`name: "adaptiveCard/action"`) |
| **구분자** | `data` 객체 내 커스텀 키 또는 `id` (`submitActionId`) | 전용 **`verb`** 필드 + `data` 객체 |
| **즉시 카드 교체** | 별도 `UpdateAsync` API 호출 필요 | **Invoke HTTP 200 응답 본문에 새 카드를 직접 반환**하여 즉시 교체 |
| **사용자별 뷰** | 미지원 (모두 동일한 카드 표시) | **`refresh` 속성으로 사용자별 다른 카드 반환 가능** |

### 4.1 `Action.Submit`을 Bot에서 쓸 때 특수 래퍼 (`msteams`)
Bot에서 `Action.Submit`을 사용할 때 `data.msteams` 객체를 넣으면 특수 동작을 수행합니다.

- **Task Module / Dialog 열기 (`task/fetch`)**:
```json
{
  "type": "Action.Submit",
  "title": "상세 편집 창 열기",
  "data": {
    "msteams": { "type": "task/fetch" },
    "ticketId": "INC-402"
  }
}
```
- **비공개 백엔드 전달 (`messageBack`)** / **공개 채팅 입력 (`imBack`)**:
```json
{
  "type": "Action.Submit",
  "title": "담당자 배정",
  "data": {
    "msteams": {
      "type": "messageBack",
      "displayText": "담당자 배정을 요청했습니다.",
      "text": "assign_ticket",
      "value": "{\"ticketId\": \"INC-402\"}"
    }
  }
}
```
> [!NOTE]
> Teams에서 `Action.Submit`의 `isEnabled` 속성은 지원되지 않으며, `style: "positive" | "destructive"` 버튼 스타일링도 플랫폼 제약으로 무시될 수 있습니다.

---

## 5. `Action.Execute` 백엔드 수신 및 응답 JSON (Universal Actions)

### 5.1 백엔드가 수신하는 `invoke` 요청 JSON (`adaptiveCard/action`)

사용자가 채널이나 그룹 채팅에서 `Action.Execute` 버튼을 클릭하면, Teams 클라이언트는 백엔드 봇 엔드포인트로 아래 `invoke` Activity JSON을 `POST`합니다.

```json
{
  "type": "invoke",
  "name": "adaptiveCard/action",
  "id": "f:1234567890",
  "timestamp": "2026-11-17T14:15:30.000Z",
  "serviceUrl": "https://smba.trafficmanager.net/kr/",
  "channelId": "msteams",
  "from": {
    "id": "29:1AbCdEfGhIjKlMnOpQrStUvWxYz",
    "name": "김철수",
    "aadObjectId": "87d349ed-44d7-43e1-9a83-5f2406dee5bd"
  },
  "conversation": {
    "isGroup": true,
    "conversationType": "channel",
    "tenantId": "2432b57b-0abd-43db-aa7b-16eadd115d34",
    "id": "19:4a95f7d8db4c4e7fae857bcebe0623e6@thread.tacv2;messageid=1700000000000"
  },
  "replyToId": "1700000000000",
  "value": {
    "action": {
      "type": "Action.Execute",
      "id": "btnApprove",
      "verb": "approveDeploy",
      "data": {
        "deployId": "DEP-20261117-01",
        "decision": "approved",
        "deployWindow": "immediate",
        "reviewComment": "확인 완료. 배포 진행하세요.",
        "notifyChannel": "true"
      }
    },
    "trigger": "manual"
  }
}
```
- **`value.action.verb`**: 어떤 버튼이 눌렸는지 백엔드 라우팅 분기 기준으로 사용합니다.
- **`value.action.data`**: 카드 버튼에 사전 정의된 `data` + 사용자가 입력한 `Input.*` 값들이 병합되어 전달됩니다. (`associatedInputs: "auto"` 설정 시)
- **`value.trigger`**: 사용자가 직접 버튼을 눌렀을 때는 `"manual"`, 카드 `refresh` 기능에 의한 자동 갱신일 때는 `"automatic"`이 전달됩니다.

### 5.2 백엔드가 반환해야 하는 HTTP 200 Invoke 응답 JSON

백엔드는 HTTP 상태 코드 `200 OK`와 함께 응답 본문에 `statusCode`, `type`, `value`를 반환해야 합니다. (Teams 클라이언트는 HTTP 상태가 아닌 본문의 `statusCode`를 보고 성공/오류를 판단합니다.)

#### ① 처리 성공 + 새 Adaptive Card로 즉시 교체
```json
{
  "statusCode": 200,
  "type": "application/vnd.microsoft.card.adaptive",
  "value": {
    "$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
    "type": "AdaptiveCard",
    "version": "1.5",
    "body": [
      {
        "type": "TextBlock",
        "text": "✅ 배포가 승인되었습니다 (승인자: 김철수)",
        "weight": "Bolder",
        "color": "Good"
      },
      {
        "type": "TextBlock",
        "text": "선택된 배포 시간: immediate\n코멘트: 확인 완료. 배포 진행하세요.",
        "wrap": true
      }
    ]
  }
}
```

#### ② 처리 성공 + 텍스트 알림 메시지 표시 (카드는 유지)
```json
{
  "statusCode": 200,
  "type": "application/vnd.microsoft.activity.message",
  "value": "승인 요청이 정상 처리되었습니다."
}
```

#### ③ 유효성 검증 실패 / 오류 응답 (`statusCode`: 400, 401, 412, 500)
```json
{
  "statusCode": 400,
  "type": "application/vnd.microsoft.error",
  "value": {
    "code": "InvalidComment",
    "message": "반려 시에는 반드시 사유를 입력해야 합니다."
  }
}
```

---

## 6. User-Specific Views (사용자별 맞춤형 카드 자동 갱신)

채널이나 그룹 채팅에 하나의 카드를 게시했지만, **승인자(Alex)에게는 [승인/반려] 버튼**, **요청자(Megan)에게는 [수정/취소] 버튼**, **나머지 팀원에게는 [조회 전용] 카드**를 보여주고 싶을 때 `refresh` 블록을 사용합니다.

```json
{
  "$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
  "type": "AdaptiveCard",
  "version": "1.5",
  "refresh": {
    "action": {
      "type": "Action.Execute",
      "title": "새로고침",
      "verb": "loadUserSpecificView",
      "data": {
        "incidentId": "INC-1234"
      }
    },
    "userIds": [
      "29:megan_user_mri_here",
      "29:alex_user_mri_here"
    ]
  },
  "body": [
    { "type": "TextBlock", "text": "인시던트 #1234", "weight": "Bolder" },
    { "type": "TextBlock", "text": "공통 조회용 상세 내용입니다.", "wrap": true }
  ]
}
```

### 동작 방식 및 제약
1. `userIds`에 지정된 사용자(최대 **60명**, User MRI 지정)가 해당 채널/채팅을 열면 Teams가 자동으로 `trigger: "automatic"`, `verb: "loadUserSpecificView"`로 백엔드에 `invoke`를 전송합니다.
2. 백엔드는 `activity.from`을 확인한 뒤 해당 사용자 권한에 맞는 Adaptive Card JSON을 응답으로 내려줍니다.
3. 자동 갱신은 마지막 호출로부터 **1분 이상** 경과한 경우에만 재실행되며, 사용자별 캐시 카드는 클라이언트당 최대 50개까지 보관됩니다.
