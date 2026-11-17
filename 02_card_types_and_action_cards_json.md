# 02. MS Teams 알림 카드 및 액션 카드 백엔드 JSON 형식

Microsoft Teams는 Adaptive Card 외에도 목적에 맞는 다양한 카드 유형(Bot Framework 기반 카드 및 Connector 카드)을 지원합니다. 각 카드별 **`contentType`**, **JSON 구조**, **버튼 액션 종류**, **기능별 지원 여부**를 정리합니다.

---

## 1. 카드 유형별 지원 매트릭스

| 카드 유형 | `contentType` 값 | Bots | Workflows Webhook | Outgoing Webhook | Message Extension | 비고 |
| :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| **Adaptive Card** | `application/vnd.microsoft.card.adaptive` | ✅ | ✅ | ✅ | ✅ | **가장 권장되는 표준 카드** (v1.6 이하) |
| **Connector Card (MessageCard)** | `application/vnd.microsoft.teams.card.o365connector` (봇) / 루트 JSON (웹훅) | ✅ | ✅ (렌더링만) | ❌ | ✅ | 레거시 알림용. Workflows 웹훅에서 `HttpPOST` 미지원 |
| **Hero Card** | `application/vnd.microsoft.card.hero` | ✅ | ❌ (일부 클라이언트 비권장) | ✅ | ✅ | 대형 상단 이미지(16:9) + 제목 + 최대 6개 버튼 |
| **Thumbnail Card** | `application/vnd.microsoft.card.thumbnail` | ✅ | ❌ | ✅ | ✅ | 소형 정사각 썸네일(1:1) + 요약 + 버튼 |
| **List Card** | `application/vnd.microsoft.teams.card.list` | ✅ | ❌ | ✅ | ❌ | 스크롤 가능한 목록형 카드 (Teams 전용) |
| **Receipt Card** | `application/vnd.microsoft.card.receipt` | ✅ | ❌ | ❌ | ✅ | 결제/주문 영수증 카드 |
| **Sign-in Card** | `application/vnd.microsoft.card.signin` | ✅ | ❌ | ❌ | ❌ | OAuth 로그인 유도 카드 |

> [!NOTE]
> **Bot Framework 카드 중 Teams에서 미지원되는 유형**: Animation Card, Audio Card, Video Card는 Teams에서 지원되지 않습니다.

---

## 2. Connector Card (Actionable Message Card / `MessageCard`)

기존 Office 365 Connectors 및 알림 시스템에서 가장 널리 쓰이던 **액션 카드(Actionable Message)** 포맷입니다.

### 2.1 Webhook 직접 전송 vs Bot 첨부 차이
- **Webhook 전송 시**: JSON 최상위에 `@type: "MessageCard"` 객체를 바로 보냅니다.
- **Bot 전송 시**: `attachments` 배열 안에 `contentType: "application/vnd.microsoft.teams.card.o365connector"`, `content: { ...MessageCard JSON... }` 형태로 감싸서 보냅니다.

### 2.2 전체 JSON 예시 (`ActionCard` 입력 폼 + `HttpPOST` + `OpenUri`)

```json
{
  "@type": "MessageCard",
  "@context": "http://schema.org/extensions",
  "themeColor": "0076D7",
  "summary": "장애 티켓 #402 할당 알림",
  "title": "신규 장애 티켓 등록",
  "sections": [
    {
      "activityTitle": "홍길동님이 티켓을 등록했습니다",
      "activitySubtitle": "결제 모듈 장애 대응 프로젝트",
      "activityImage": "https://example.com/avatar.png",
      "activityImageType": "avatar",
      "facts": [
        { "name": "담당자", "value": "미지정" },
        { "name": "마감일", "value": "2026-11-18 18:00 KST" },
        { "name": "심각도", "value": "High" }
      ],
      "markdown": true,
      "text": "결제 승인 API에서 간헐적 `504 Gateway Timeout`이 발생하고 있습니다."
    }
  ],
  "potentialAction": [
    {
      "@type": "ActionCard",
      "@id": "addCommentAction",
      "name": "코멘트 추가",
      "inputs": [
        {
          "@type": "TextInput",
          "id": "comment",
          "isMultiline": false,
          "title": "처리 코멘트를 입력하세요"
        }
      ],
      "actions": [
        {
          "@type": "HttpPOST",
          "name": "저장",
          "target": "https://api.example.com/teams/ticket/402/comment",
          "body": "{\"comment\": \"{{comment.value}}\"}"
        }
      ]
    },
    {
      "@type": "ActionCard",
      "@id": "setDueDateAction",
      "name": "마감일 지정",
      "inputs": [
        {
          "@type": "DateInput",
          "id": "dueDate",
          "title": "마감 날짜 선택"
        }
      ],
      "actions": [
        {
          "@type": "HttpPOST",
          "name": "적용",
          "target": "https://api.example.com/teams/ticket/402/duedate"
        }
      ]
    },
    {
      "@type": "ActionCard",
      "@id": "changeStatusAction",
      "name": "상태 변경",
      "inputs": [
        {
          "@type": "MultichoiceInput",
          "id": "statusList",
          "title": "새 상태를 선택하세요",
          "isMultiSelect": false,
          "style": "compact",
          "choices": [
            { "display": "진행 중 (In Progress)", "value": "in_progress" },
            { "display": "해결됨 (Resolved)", "value": "resolved" },
            { "display": "종료 (Closed)", "value": "closed" }
          ]
        }
      ],
      "actions": [
        {
          "@type": "HttpPOST",
          "name": "상태 저장",
          "target": "https://api.example.com/teams/ticket/402/status"
        }
      ]
    },
    {
      "@type": "OpenUri",
      "name": "티켓 상세 보기",
      "targets": [
        { "os": "default", "uri": "https://jira.example.com/browse/INC-402" }
      ]
    }
  ]
}
```

### 2.3 Connector Card 주요 속성 및 제한사항
- **`summary`**: `text`가 없을 때 필수이며, Teams 팝업 알림(Toast) 미리보기에 표시됩니다.
- **`themeColor`**: 상단 강조 테두리 HEX 색상 (예: `"FF0000"`).
- **`sections`**: 최대 **10개** 섹션, 섹션당 최대 5개 이미지 / 5개 액션 지원.
- **`activityImageType`**: `"avatar"`(원형 자르기, 기본값) 또는 `"article"`(직사각형 유지).
- **`markdown`**: 기본값 `true`. HTML을 쓰려면 `false`로 설정(제한적 HTML 태그만 지원).
- **Teams 미지원 속성**: `heroImage`, `hideOriginalBody`, `originator`, `correlationId`는 무시되며 `startGroup`은 항상 `true`로 처리됩니다.
- **액션 동작 차이**: Bot으로 전송 시 `HttpPOST`는 외부 URL로 직접 나가지 않고 Bot의 `invoke` Activity(`signin/action` 또는 `o365ConnectorCardAction`)로 전달됩니다. Workflows Webhook에서는 `HttpPOST`가 지원되지 않습니다.

---

## 3. Hero Card (히어로 카드)

대형 배너 이미지(16:9 비율)와 제목, 부제목, 본문 및 최대 6개의 액션 버튼을 배치하는 카드입니다.

```json
{
  "type": "message",
  "attachments": [
    {
      "contentType": "application/vnd.microsoft.card.hero",
      "content": {
        "title": "주간 서비스 운영 리포트",
        "subtitle": "2026년 11월 2주차 요약",
        "text": "전체 가용률 **99.98%** 달성. 주요 장애 건수는 0건입니다.",
        "images": [
          {
            "url": "https://cdn.example.com/reports/weekly-banner.png",
            "alt": "운영 리포트 배너 이미지"
          }
        ],
        "buttons": [
          {
            "type": "openUrl",
            "title": "대시보드 열기",
            "value": "https://dashboard.example.com/weekly"
          },
          {
            "type": "messageBack",
            "title": "상세 지표 요청",
            "displayText": "상세 지표 요청함",
            "text": "get_weekly_metrics",
            "value": { "week": "2026-W46", "action": "detail" }
          }
        ],
        "tap": {
          "type": "openUrl",
          "value": "https://dashboard.example.com/weekly"
        }
      }
    }
  ]
}
```

### Hero / Thumbnail Card 버튼 액션 타입 (`CardAction.type`)

| 버튼 `type` | 동작 방식 | 백엔드 수신 형태 |
| :--- | :--- | :--- |
| `openUrl` | 지정된 URL을 브라우저/앱에서 엶 | 백엔드 호출 없음 |
| `imBack` | `value` 문자열을 사용자가 직접 채팅창에 입력한 것처럼 공개 메시지로 전송 | `activity.text`로 수신 (모든 채널/채팅 참여자에게 보임) |
| `messageBack` | `displayText`만 채팅창에 표시(생략 시 미표시)하고, `text` 및 `value` JSON을 봇에게 비공개 전달 | `activity.text` + `activity.value` 객체로 수신 |
| `invoke` | 채팅창에 메시지를 남기지 않고 봇에게 비공개 `invoke` 요청 전송 (Task Module / Dialog 열기 등) | `activity.type === "invoke"`, `activity.value`로 수신 |
| `signin` | OAuth 로그인 창 실행 | 인증 토큰 콜백 |

---

## 4. Thumbnail Card (썸네일 카드)

우측(또는 상단)에 1:1 정사각형 썸네일 이미지를 배치하는 간결한 알림 카드입니다.

```json
{
  "type": "message",
  "attachments": [
    {
      "contentType": "application/vnd.microsoft.card.thumbnail",
      "content": {
        "title": "PR #128 리뷰 요청",
        "subtitle": "feat: 결제 타임아웃 재시도 로직 추가",
        "text": "작성자: 이영희 | 변경 파일: 7개 (+214 / -45)",
        "images": [
          {
            "url": "https://cdn.example.com/icons/pull-request.png",
            "alt": "PR 아이콘"
          }
        ],
        "buttons": [
          {
            "type": "openUrl",
            "title": "GitHub에서 보기",
            "value": "https://github.com/org/repo/pull/128"
          },
          {
            "type": "imBack",
            "title": "리뷰 승인",
            "value": "/approve 128"
          }
        ],
        "tap": {
          "type": "openUrl",
          "value": "https://github.com/org/repo/pull/128"
        }
      }
    }
  ]
}
```

---

## 5. List Card (리스트 카드, Teams 전용)

여러 파일, 검색 결과, 담당자 목록을 스크롤 가능한 리스트 형태로 표시합니다.

```json
{
  "type": "message",
  "attachments": [
    {
      "contentType": "application/vnd.microsoft.teams.card.list",
      "content": {
        "title": "관련 문서 및 담당자",
        "items": [
          {
            "type": "file",
            "id": "https://contoso.sharepoint.com/sites/dev/Shared%20Documents/Architecture.docx",
            "title": "Architecture.docx",
            "subtitle": "dev > Shared Documents",
            "tap": {
              "type": "imBack",
              "value": "open_doc Architecture.docx"
            }
          },
          {
            "type": "resultItem",
            "icon": "https://cdn.example.com/icons/jira.png",
            "title": "INC-402: 결제 API 지연",
            "subtitle": "상태: 진행 중 | 우선순위: P1",
            "tap": {
              "type": "openUrl",
              "value": "https://jira.example.com/browse/INC-402"
            }
          },
          {
            "type": "section",
            "title": "온콜 담당자"
          },
          {
            "type": "person",
            "id": "kim@contoso.com",
            "title": "김철수",
            "subtitle": "SRE Lead",
            "tap": {
              "type": "imBack",
              "value": "whois kim@contoso.com"
            }
          }
        ],
        "buttons": [
          {
            "type": "imBack",
            "title": "전체 목록 새로고침",
            "value": "refresh_list"
          }
        ]
      }
    }
  ]
}
```

---

## 6. Receipt Card & Sign-in Card

### 6.1 Receipt Card (영수증/정산 내역 카드)

```json
{
  "type": "message",
  "attachments": [
    {
      "contentType": "application/vnd.microsoft.card.receipt",
      "content": {
        "title": "클라우드 리소스 월간 청구서",
        "facts": [
          { "key": "청구 번호", "value": "INV-2026-1101" },
          { "key": "결제 수단", "value": "법인카드 5555-****" }
        ],
        "items": [
          {
            "title": "Kubernetes Cluster (AKS)",
            "subtitle": "Production Node Pool x 6",
            "price": "$ 420.00",
            "quantity": "1",
            "image": { "url": "https://cdn.example.com/icons/aks.png" }
          },
          {
            "title": "Managed PostgreSQL",
            "price": "$ 180.00",
            "quantity": "1",
            "image": { "url": "https://cdn.example.com/icons/db.png" }
          }
        ],
        "tax": "$ 60.00",
        "total": "$ 660.00",
        "buttons": [
          {
            "type": "openUrl",
            "title": "비용 상세 내역",
            "value": "https://portal.azure.com"
          }
        ]
      }
    }
  ]
}
```

### 6.2 Sign-in Card (인증 카드)

Teams에서는 `signin`과 `openUrl` 두 가지 버튼 타입만 지원합니다.

```json
{
  "type": "message",
  "attachments": [
    {
      "contentType": "application/vnd.microsoft.card.signin",
      "content": {
        "text": "외부 Jira 계정을 연동하려면 로그인이 필요합니다.",
        "buttons": [
          {
            "type": "signin",
            "title": "Jira 로그인",
            "value": "https://auth.example.com/oauth/authorize?state=xyz"
          }
        ]
      }
    }
  ]
}
```

---

## 7. Card Collections (캐러셀 및 리스트 컬렉션)

단일 메시지에 여러 개의 Hero, Thumbnail, 또는 Adaptive Card(최대 **10개**)를 가로 슬라이드(`carousel`)나 세로 목록(`list`)으로 묶어 전송할 수 있습니다.

```json
{
  "type": "message",
  "attachmentLayout": "carousel",
  "attachments": [
    {
      "contentType": "application/vnd.microsoft.card.hero",
      "content": {
        "title": "옵션 1: 서울 리전",
        "subtitle": "Korea Central",
        "buttons": [
          { "type": "imBack", "title": "선택", "value": "select_kr_central" }
        ]
      }
    },
    {
      "contentType": "application/vnd.microsoft.card.hero",
      "content": {
        "title": "옵션 2: 도쿄 리전",
        "subtitle": "Japan East",
        "buttons": [
          { "type": "imBack", "title": "선택", "value": "select_jp_east" }
        ]
      }
    }
  ]
}
```

---

## 8. 모든 카드 공통 제약사항

1. **이미지 규칙**:
   - 공개적으로 접근 가능한 **HTTPS URL** 사용 권장 (또는 Adaptive Card 내 제한적 Base64 Data URI).
   - 지원 포맷: **PNG, JPEG, GIF** (애니메이션 GIF 및 SVG 미지원).
   - 크기 제한: 최대 **1024 × 1024 px**, 파일 크기 **1 MB 미만**.
   - **URL 리다이렉트(HTTP 301/302) 미지원**: 이미지 URL이 리다이렉트되면 카드에서 이미지가 깨집니다.
2. **텍스트 및 버튼**:
   - 버튼 텍스트는 항상 **한 줄**로 렌더링되며 길면 말줄임(`...`) 처리됩니다.
   - `title`, `subtitle`은 최대 2줄까지만 표시되며 Markdown/HTML 서식이 적용되지 않습니다(`text` 필드에만 서식 지원).
