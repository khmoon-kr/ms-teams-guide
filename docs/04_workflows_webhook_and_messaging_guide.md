# 04. Workflows(Power Automate) 기반 채널·그룹 채팅 메시지 구현 가이드

2026년 5월 Office 365 Connectors 은퇴 이후, Microsoft Teams는 **Workflows 앱(내부적으로 Microsoft Power Automate 기반)** 을 통해 외부 백엔드 Webhook 수신, 카드 게시, 사용자 응답 수집을 구현하는 것을 표준으로 제공합니다.

---

## 1. Workflows 앱 핵심 구조 및 관리 주의사항

- **소유권 모델**: Workflows는 채널/팀이 아니라 **특정 사용자(소유자, Owner)** 의 연결(Connection)에 귀속됩니다. 담당자가 퇴사하면 플로우가 중단되는 고아 플로우(Orphan Flow)가 되므로, 반드시 **공동 소유자(Co-owners)** 를 추가하고 필요 시 서비스 계정을 사용하는 것이 좋습니다.
- **발신자(`Post as`) 옵션**:
  - `Flow bot`: 개인 계정이 아닌 "Workflows" 봇 이름으로 메시지/카드를 게시 (채널/그룹 채팅 알림에 권장). *단, 정부 클라우드(GCC/GCCH/DoD)에서는 미지원.*
  - `User`: 플로우 연결에 로그인한 사용자 계정으로 직접 게시.
  - `Microsoft Copilot Studio agent`: 등록된 Copilot 에이전트 명의로 게시.

---

## 2. 패턴 A: 외부 백엔드 → Workflows Webhook → 채널/그룹 채팅 전송

외부 서버(GitHub, Prometheus, 자체 백엔드 등)에서 HTTP POST로 채널이나 그룹 채팅에 알림 카드를 보내는 방법입니다.

### 2.1 템플릿으로 빠르게 생성하기 (채널 알림)
1. Microsoft Teams에서 대상 **팀 → 채널 우측 `...` (옵션 더 보기) → Workflows(워크플로우)** 선택.
2. **`Send webhook alerts to a channel` (웹후크 경고를 채널에 보내기)** 템플릿 선택.
3. 워크플로우 이름, 대상 **Team** 및 **Channel** 확인 후 저장(**Save**).
4. 생성된 **Webhook URL**을 복사하여 백엔드에 등록.

### 2.2 빈 흐름(Create from blank)으로 커스텀 구현하기 (그룹 채팅 포함)

그룹 채팅으로 Webhook 알림을 보내거나, 백엔드의 **커스텀 JSON**을 받아 Workflows 내부에서 Adaptive Card로 변환하려면 직접 조립합니다.

1. Teams 내 **Workflows 앱 → 관리(Manage) → 새로 만들기(Create) → 빈 상태에서 만들기(Create from blank)** (또는 Power Automate 포털).
2. **트리거(Trigger) 추가**: `Microsoft Teams` 커넥터 → **`When a Teams webhook request is received`** 선택.
   - **Who can trigger the flow (트리거 권한 설정)**:
     - `Anyone`: URL만 알면 외부에서 호출 가능 (**주의: `Authorization` 헤더를 전달하면 요청이 실패하므로 헤더 없이 호출해야 함**).
     - `Any user in my tenant`: 동일 테넌트 Entra ID OAuth 토큰 필수.
     - `Specific users in my tenant`: 지정된 사용자만 호출 가능.
3. *(중요)* 템플릿이나 기본 생성 시 자동으로 붙는 **`Send each adaptive card` (Apply to each)** 루프는 커스텀 JSON 파싱 시 방해가 되므로, 커스텀 파싱이 필요하면 해당 단계를 삭제합니다.
4. **액션 1 (`Data Operation - Parse JSON`) 추가**:
   - **Content**: `@{triggerBody()}` (동적 콘텐츠에서 `Body` 선택)
   - **Schema**: 백엔드가 보낼 JSON 샘플을 `Use sample payload to generate schema`로 입력.
5. **액션 2 (`Microsoft Teams - Post card in a chat or channel`) 추가**:
   - **Post as**: `Flow bot` (또는 `User`)
   - **Post in**: `Channel` 또는 **`Group chat`** 선택
   - **Team / Channel** 또는 **Group chat**: 대상 선택 *(참고: Group chat 드롭다운에는 최근 이름 있는 그룹 채팅 50개만 노출됨)*
   - **Adaptive Card**: Adaptive Card JSON 입력 (동적 콘텐츠 토큰 삽입 가능)
6. **저장(Save)** 후 트리거 카드 상단에 생성된 **HTTP POST URL** 복사.

### 2.3 백엔드 호출 예제 (cURL & Python)

```bash
curl -X POST "<WORKFLOW_WEBHOOK_URL>" \
  -H "Content-Type: application/json" \
  -d '{
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
            { "type": "TextBlock", "text": "🚀 배포 완료: v2.1.0", "weight": "Bolder" },
            { "type": "TextBlock", "text": "대상 환경: Production", "wrap": true }
          ]
        }
      }
    ]
  }'
```

```python
import requests

webhook_url = "<WORKFLOW_WEBHOOK_URL>"
payload = {
    "type": "message",
    "attachments": [
        {
            "contentType": "application/vnd.microsoft.card.adaptive",
            "contentUrl": None,
            "content": {
                "$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
                "type": "AdaptiveCard",
                "version": "1.4",
                "body": [
                    {"type": "TextBlock", "text": "⚠️ CPU 사용률 92% 초과", "color": "Attention", "weight": "Bolder"},
                    {"type": "TextBlock", "text": "노드: prod-worker-03", "wrap": True}
                ]
            }
        }
    ]
}
resp = requests.post(webhook_url, json=payload, timeout=10)
print(resp.status_code)  # 202 Accepted
```

> [!TIP]
> 일부 CI/CD 도구나 쉘 환경에서는 복사한 Webhook URL의 쿼리 파라미터(`%2F` 등)가 이중 인코딩되는 문제가 있을 수 있으므로 URL 인코딩 상태를 확인하세요.

---

## 3. 패턴 B: 양방향 카드 응답 처리 (승인 / 입력 폼)

채널이나 그룹 채팅에 Adaptive Card를 보내고, **사용자가 버튼을 클릭하거나 폼을 제출한 결과를 받아 후속 처리**하는 방법은 두 가지가 있습니다.

### 3.1 방법 1: `Post adaptive card and wait for a response` (단일 플로우 동기 대기 — 공식 권장)

하나의 플로우가 카드를 게시하고, 누군가 응답할 때까지 대기한 뒤 결과를 받아 다음 단계(DB 저장, API 호출, 회신 등)를 실행합니다.

1. 액션 추가: `Microsoft Teams` → **`Post adaptive card and wait for a response`**
2. 주요 파라미터 설정:
   - **Post as**: `Flow bot` (또는 `User`, `Copilot Studio agent`)
   - **Post in**: `Channel`, `Group chat`, 또는 `Chat with Flow bot`
   - **Message**: `Action.Submit`이 포함된 Adaptive Card JSON 붙여넣기 *(주의: Power Automate 액션에서는 `${...}` Adaptive Card Templating 문법을 지원하지 않으므로, Power Automate 동적 콘텐츠 토큰을 JSON 문자열 안에 직접 삽입해야 함)*
   - **Update message**: 사용자가 제출한 직후 카드를 대체할 감사/완료 문구 (예: `"응답이 접수되었습니다. 감사합니다."`)
   - **Should update card**: `Yes` (중복 제출 방지)
3. **응답 JSON 구조 및 파싱**:
   - 사용자가 카드를 제출하면 해당 액션의 `body`에 아래와 유사한 JSON이 반환됩니다.
   ```json
   {
     "responder": {
       "id": "87d349ed-44d7-43e1-9a83-5f2406dee5bd",
       "displayName": "김철수",
       "email": "chulsoo.kim@contoso.com",
       "userPrincipalName": "chulsoo.kim@contoso.com"
     },
     "submitActionId": "btnApprove",
     "data": {
       "deployWindow": "immediate",
       "reviewComment": "승인합니다."
     }
   }
   ```
   - 후속 액션에서 `outputs('Post_adaptive_card_and_wait_for_a_response')?['body/submitActionId']` 또는 **`Parse JSON`** 액션을 통해 입력 필드 ID(`data.reviewComment` 등)와 제출 버튼(`submitActionId`)을 꺼내 `Condition` 분기 처리합니다.
   - 필요 시 `HTTP` 액션을 추가하여 사내 백엔드 서버로 최종 결과를 `POST` 전송할 수 있습니다.

### 3.2 방법 2: 분리형 리스너 (`Post card in a chat or channel` + `When someone responds to an adaptive card`)

플로우를 오래 대기 상태로 두지 않거나 여러 번의 응답을 비동기로 받으려면 **발송 플로우**와 **수신 플로우(Listener)** 로 분리할 수 있습니다.

- **발송 플로우**: `Post card in a chat or channel` 사용 (`Post as: Flow bot` 필수, 고급 매개변수 `Advanced parameters` 내 **`Card Type ID`** 에 고유 식별자 예: `DeployApprovalCard` 입력).
- **수신 플로우**: 트리거로 **`When someone responds to an adaptive card`** 선택.

> [!CAUTION]
> **분리형 리스너 구현 시 필수 체크리스트 (실무 장애 예방)**
> 1. **Default 환경 필수**: `When someone responds to an adaptive card` 트리거는 Power Platform의 **Default Environment**에서만 동작합니다. 비-기본 환경에 만들면 응답이 전달되지 않습니다.
> 2. **Split On 끄기**: 트리거 생성 시 `Settings → Split On`이 기본으로 켜져 있을 수 있으며, 이 경우 단일 Submit 응답에서 플로우가 조용히 실행되지 않습니다. **반드시 `Split On`을 `Off`로 변경**하세요.
> 3. **Card Type ID 일치**: 발송 액션의 고급 매개변수 `Card Type ID`와 수신 트리거의 `Card Type ID`가 **대소문자까지 정확히 일치**해야 합니다. 불일치 시 Teams에서 `"Unable to reach app. Please try again."` 오류가 납니다.
> 4. **Classic Designer 사용 및 데이터 경로**: Classic Designer에서 발송 시와 동일한 카드 JSON을 트리거에 붙여넣어 스키마를 매핑하며, 실제 제출 데이터는 `triggerOutputs()?['body/entity/cardOutputs']` 경로에 들어옵니다.
> 5. **스레드 답글 미지원**: `Reply with an adaptive card in a channel` 액션에는 `Card Type ID`가 없어 `When someone responds...` 트리거로 수신할 수 없습니다. 스레드 답글 응답이 필요하면 `Wait for a response` 방식이나 Bot을 사용해야 합니다.

---

## 4. 패턴 C: 채널/채팅 메시지 수신 트리거 및 `@mention` 처리

### 4.1 메시지 기반 트리거 종류

| 트리거 이름 | 동작 방식 | 제한사항 및 특징 |
| :--- | :--- | :--- |
| **`When a new channel message is added`** | 채널 새 루트 메시지 감지 | **3분 폴링 간격** (실시간 아님). 기존 글의 **답글(Reply)에는 트리거되지 않음**. |
| **`When a new chat message is added`** | 채팅(1:1/그룹) 새 메시지 감지 | 플로우당 연결된 **사용자 1명**이 참여한 채팅만 감지. |
| **`When keywords are mentioned`** | 특정 키워드 포함 시 실행 | **단일 단어(1 word)** 만 지원 (공백 포함 구문 불가). |
| **`For a selected message (V2)`** | 메시지 `... → 추가 작업`에서 수동 실행 | **즉시 트리거(Instant)**. Default 환경 필수. 실행 전 미니 Adaptive Card 입력 팝업 제공 가능. |

### 4.2 Workflows에서 사용자/태그 `@mention` 넣기
1. 메시지/카드 게시 액션 바로 위에 **`Get an @mention token for a user`** 액션 추가 → 대상 사용자 이메일 입력. *(여러 명 멘션 시 인원수만큼 액션 추가, 한 메시지에 최대 20명/20태그 제한)*
2. 채널 내 팀 태그 멘션 시 **`Get an @mention token for a team tag`** 사용.
3. 생성된 동적 콘텐츠 **`@mention`** 토큰을 `Post message in a chat or channel` 또는 Adaptive Card JSON 내부의 `TextBlock`에 삽입합니다.

---

## 5. Workflows Teams 커넥터 공통 제한사항 요약

- **메시지 크기**: 28 KB 초과 시 `Request Entity too large` 오류.
- **비공개 채널(Private Channel)**: `Post message/card in a chat or channel` 기본 액션은 비공개 채널 포스팅을 지원하지 않습니다. (비공개 채널에는 Workflows Incoming Webhook 템플릿 또는 Graph API `Send a Microsoft Graph HTTP request` 활용 필요)
- **Throttling (호출 제한)**:
  - Flow bot 작업(`Post card...`, `List chats`, `Post feed notification` 등): 연결당 **300초 내 25회**.
  - 기타 일반 Teams 작업: 연결당 **300초 내 300회**, 60초 내 API 호출 100회.
