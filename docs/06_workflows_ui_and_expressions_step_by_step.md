# 06. Teams Workflows UI 완전 정복: 채팅에서 시작하기 · 흐름 편집기 · 수식(fx) 가이드

백엔드에서 보낼 JSON 구조는 이해했는데, 막상 **Microsoft Teams 채팅창에서 `워크플로(Workflows)`를 눌러 흐름을 만들고, 편집기에서 동적 값(`⚡`)과 수식(`fx`)을 다루려고 하면 화면과 문법이 생소해서 막히는 경우**가 많습니다.

이 문서는 Teams 화면 클릭 순서부터 Power Automate 흐름 편집기(디자이너) 조작법, 그리고 실무에서 가장 헷갈리는 **동적 콘텐츠와 수식(`fx`) 작성법**을 처음부터 끝까지 단계별로 정리한 가이드입니다.

---

## 1. 전체 작업 흐름 한눈에 보기

Teams Workflows는 겉으로는 Teams 앱처럼 보이지만, 실제 흐름을 수정하고 수식을 짜는 화면은 **Power Automate 디자이너**입니다.

```text
[1단계: Teams 채팅/채널]
  채팅창 '...' → 워크플로(Workflows) 선택
        ↓
[2단계: 템플릿 마법사]
  용도에 맞는 템플릿 고르기 → 이름·연결 확인 → 대상 채팅/채널 선택 → URL 복사
        ↓
[3단계: 흐름 편집기 (Edit)]   ← ★ 여기서부터 본격 커스터마이징
  Workflows 앱 → 관리(Manage) → 편집(Edit)
  (트리거 확인 → 불필요한 기본 루프 삭제 → + 버튼으로 액션 추가)
        ↓
[4단계: 동적 콘텐츠(⚡) & 수식(fx)]
  이전 단계 JSON 값을 꺼내 가공하고 Adaptive Card / 조건 분기에 바인딩
        ↓
[5단계: 저장(Save) & 실행 기록(Run history) 디버깅]
  테스트 요청 전송 → 실행 기록에서 실제 INPUTS / OUTPUTS JSON 확인
```

---

## 2. Step 1: 채팅방에서 워크플로 선택 후 무엇을 눌러야 하나?

### 2.1 진입 위치 3가지 (어디서 시작할까?)

| 진입 위치 | 클릭 경로 | 언제 쓰나? |
| :--- | :--- | :--- |
| **그룹/1:1 채팅방** | 채팅 목록 또는 채팅방 상단 **`...` (옵션 더 보기)** → **`워크플로 (Workflows)`** | **현재 열려 있는 채팅방**으로 알림이나 카드를 보내고 싶을 때 (대상 채팅이 자동 선택됨) |
| **팀 채널** | 채널 이름 옆 **`...` (옵션 더 보기)** → **`워크플로 (Workflows)`** | **특정 채널**로 알림을 보낼 때 |
| **Workflows 앱 (좌측 메뉴)** | Teams 좌측 앱 바 **`...`** → `Workflows` 검색/실행 → **`+ 새 흐름 (New flow)`** | 새 채팅방/채널을 동적으로 만들거나, **빈 상태에서 직접** 복잡한 흐름을 조립할 때 |

---

### 2.2 채팅에서 열었을 때 템플릿 무엇을 고를까?

채팅방 `... → 워크플로`를 누르면 템플릿 팝업이 뜹니다. 목적에 따라 아래 기준으로 고르면 됩니다.

#### 목적 ①: 외부 백엔드(서버)에서 HTTP POST로 채팅방에 알림/카드를 보내고 싶다
검색창에 **`웹후크`** 또는 **`webhook`** 을 입력하면 보통 3가지 템플릿이 나옵니다.

| 템플릿 이름 (한국어 / 영어) | 누가 호출할 수 있나? | 추천 여부 |
| :--- | :--- | :---: |
| **`채팅에 웹후크 경고 보내기`**<br>*(Post to a chat when a webhook request is received / Send webhook alerts to a chat)* | **Anyone (누구나)** — 생성된 URL만 있으면 백엔드에서 토큰 없이 바로 POST 가능 | **★ 백엔드 연동 1순위** |
| **`특정 사용자의 웹후크 알림을 채팅으로 보내기`**<br>*(Send webhook alerts from specific users to a chat)* | 지정된 사내 M365 사용자 (Entra ID OAuth 토큰 필요) | 사내 시스템/유저 인증용 |
| **`조직 내 사용자의 웹후크 경고를 채팅으로 보내기`**<br>*(Send webhook alerts from users in your org to a chat)* | 같은 회사(테넌트) 사용자 (Entra ID OAuth 토큰 필요) | 사내 전용 제한 시 |

> [!TIP]
> **채팅방에서 `빈 상태에서 만들기 (Create from blank)`를 눌렀는데 웹후크 트리거가 안 보일 때**
> Teams 내부의 간소화 팝업("Starts when...")에는 기본 트리거 몇 개만 노출됩니다. 이때 하단이나 우측의 **`Power Automate로 빌드 (Build with Power Automate)`** 링크를 누르면 전체 트리거(`When a Teams webhook request is received` 포함)를 검색할 수 있는 정식 편집기가 열립니다.

#### 목적 ②: 채팅 메시지에 반응해서 자동 처리하고 싶다
* **`채팅에 새 메시지가 추가될 때`** *(When a new chat message is added)*: 누군가 채팅방에 글을 쓰면 트리거됨.
* **`특정 키워드가 멘션될 때`** *(When keywords are mentioned)*: `!장애`, `승인요청` 같은 단일 키워드가 올라올 때 실행.

---

### 2.3 템플릿 선택 후 3단계 마법사 클릭 순서

`채팅에 웹후크 경고 보내기`를 선택했다고 가정한 순서입니다.

1. **1화면 (이름 및 연결 확인)**:
   * **흐름 이름(Name)**: 알아보기 쉽게 변경 (예: `결제서버-장애알림-채팅방`).
   * **로그인 연결(Connections)**: Microsoft Teams 아이콘 옆에 **초록색 체크(✓)** 가 떠 있는지 확인 → **`다음 (Next)`** 클릭.
   * *(팁: 퇴사·계정 변경 시 흐름이 끊기지 않으려면 개인 계정 대신 공용 서비스 계정 연결 권장)*
2. **2화면 (세부 정보 — 대상 채팅 선택)**:
   * **채팅(Chat)** 드롭다운에 현재 채팅방 이름이 자동으로 선택되어 있는지 확인 (목록 로딩에 2~3초 걸릴 수 있음).
   * ※ 주의: 드롭다운에는 **최근 이름이 지정된 채팅 50개**까지만 나타납니다. 원하는 방이 안 보이면 채팅방 상단 연필 아이콘으로 **그룹 채팅방 이름을 먼저 지정**한 뒤 다시 여세요.
   * **`워크플로 추가 (Add workflow)`** (또는 `흐름 만들기 / Create flow`) 클릭.
3. **3화면 (URL 복사)**:
   * 생성 완료 화면에 나오는 **긴 URL(HTTP POST URL)** 옆 복사 버튼을 눌러 백엔드에 저장한 뒤 **`완료 (Done)`** 클릭.
   * *(나중에 URL을 다시 보고 싶다면: Workflows 앱 → 관리 → 해당 워크플로 클릭 → 편집 → 맨 위 트리거 박스 클릭 → `HTTP POST URL` 확인)*

---

## 3. Step 2: 흐름(Workflow) 편집기 화면 이해하기

템플릿으로 만든 상태 그대로 쓰면 **"Adaptive Card 전체 JSON"** 만 그대로 전달할 수 있습니다.
백엔드가 보낸 일반 JSON(`{"service": "pay", "error": "timeout"}`)을 가공하거나, 조건(`Condition`)을 걸고, 새 채팅을 만들려면 **편집기(디자이너)** 로 들어가야 합니다.

### 3.1 편집기 여는 법 & "새 디자이너 vs 클래식 디자이너"

1. Teams 좌측 메뉴 **`Workflows`** 앱 클릭 → 상단 **`관리 (Manage)`** (또는 `내 흐름`) 탭 클릭.
2. 방금 만든 워크플로 클릭 → 상단 **`편집 (Edit)`** 버튼 클릭.
3. **우측 상단 토글 확인**: **`새 디자이너 (New designer)`** 스위치가 있습니다.

| 구분 | 새 디자이너 (New Designer, 기본값) | 클래식 디자이너 (Classic Designer) |
| :--- | :--- | :--- |
| **화면 구성** | 중앙 캔버스(순서도) + 왼쪽에 설정 패널 열림 | 세로 카드가 위에서 아래로 펼쳐지는 아코디언 형태 |
| **장점** | `fx` 수식 편집창이 크고 복사/붙여넣기가 안정적.<br>입력칸에 `/` 만 쳐도 동적 콘텐츠·수식 메뉴가 뜸. | 모든 고급 옵션이 한눈에 보이고, 일부 구형 트리거/액션 호환성이 높음. |
| **언제 전환하나?** | 일반적인 웹후크·카드·조건 작업 대부분 | **`When someone responds to an adaptive card`** 등 일부 고급 기능이 새 디자이너에서 안 뜰 때 스위치를 꺼서(`Off`) 클래식으로 전환 |

---

### 3.2 템플릿 기본 구조 뜯어보기 (가장 많이 막히는 부분!)

`채팅에 웹후크 경고 보내기` 템플릿을 편집기로 열면 보통 아래 2개 블록이 들어있습니다.

```text
[1] When a Teams webhook request is received   ← 트리거 (외부 요청 받는 입구)
        │
        ▼
[2] Send each adaptive card                    ← ★ 요주의! "Apply to each" 반복문
     └─ [2-1] Post card in a chat or channel   ← 실제로 채팅방에 카드를 쏘는 액션
```

> [!IMPORTANT]
> **`Send each adaptive card` 반복문은 언제 지우나요?**
> * 백엔드가 이미 `{"type": "message", "attachments": [...]}` 포맷으로 완성된 Adaptive Card를 보낸다면 **그대로 두면 됩니다.**
> * 반면 백엔드가 `{"title": "배포완료", "version": "1.2.0"}` 같은 **일반 커스텀 JSON**을 보내고, Workflows 안에서 카드를 직접 조립하려면:
>   1. **`Send each adaptive card` 블록 우측 `...` → `삭제 (Delete)`** 로 지웁니다.
>   2. 트리거 아래 **`+` 버튼 → `작업 추가 (Add an action)`** 를 눌러 아래와 같이 새로 조립합니다.

#### 커스텀 JSON 수신 시 권장 흐름 구조

```text
[1] When a Teams webhook request is received   (트리거)
        │
        ▼  (+ 버튼으로 추가)
[2] JSON 구문 분석 (Parse JSON)                (Data Operation 커넥터)
        │  - Content: 트리거의 Body (⚡)
        │  - Schema: 백엔드 JSON 샘플 붙여넣어 자동 생성
        ▼  (+ 버튼으로 추가)
[3] 조건 (Condition) [선택]                    (예: severity가 'CRITICAL'일 때만)
        │
        ▼  (+ 버튼으로 추가)
[4] 채팅 또는 채널에 카드 게시                  (Microsoft Teams 커넥터)
    (Post card in a chat or channel)
       - Post as: Flow bot
       - Post in: Group chat
       - Group chat: 대상 방 선택 (또는 동적 Conversation ID)
       - Adaptive Card: 카드 JSON 입력 + ⚡/fx로 필드 값 끼워 넣기
```

### 3.3 `JSON 구문 분석 (Parse JSON)` 설정하는 법

백엔드가 보낸 JSON의 키(`service`, `message` 등)를 뒤 단계에서 **번개(`⚡`) 아이콘**으로 편하게 클릭해서 꺼내 쓰려면 `Parse JSON`을 중간에 넣는 것이 가장 쉽습니다.

1. `+` → `Parse JSON` (한국어: `JSON 구문 분석`, `데이터 작업 / Data Operation` 소속) 추가.
2. **Content (콘텐츠)** 칸 클릭 → 오른쪽/옆에 뜨는 **번개(`⚡`) 아이콘** 클릭 → **`Body`** 선택.
   * ⚠️ **절대 주의**: Content 칸에 키보드로 `triggerBody()` 라는 글자를 **일반 텍스트로 그냥 타이핑하면 안 됩니다!** 반드시 **`⚡`에서 `Body`를 선택**하거나 **`fx` 창에 `triggerBody()`를 입력**해야 합니다.
3. **Schema (스키마)** 칸 아래 **`샘플 페이로드에서 생성 (Use sample payload to generate schema)`** 클릭.
4. 백엔드가 실제로 보낼 JSON 예시(예: `{"service": "auth-api", "env": "prod", "users": ["a@co.com"]}`)를 그대로 붙여넣고 **완료(Done)** 클릭 → 스키마 자동 생성!

> [!TIP]
> **Schema에서 Null 에러 방지 팁**: 어떤 필드가 가끔 `null`로 올 수 있다면, 자동 생성된 스키마의 `"type": "string"`을 `"type": ["string", "null"]` 또는 아예 해당 필드의 `"type"` 줄을 지워두면 null 값 때문에 흐름이 실패하는 것을 막을 수 있습니다.

---

## 4. Step 3: 동적 콘텐츠(`⚡`) vs 수식(`fx`) 완벽 정리

흐름을 만들다 보면 입력칸 옆에 항상 두 개의 아이콘이 나타납니다.

* **⚡ 동적 콘텐츠 (Dynamic content)**: 이전 단계(`Parse JSON`, `Create a chat` 등)가 만들어낸 출력 변수를 **마우스 클릭으로 바로 넣는 기능**.
* **fx 식 (Expression)**: 엑셀 함수처럼 **데이터를 가공·변환·추출하는 수식을 작성하는 기능**.

### 4.1 입력칸에 넣는 3가지 조작법

1. **새 디자이너에서 빠른 입력 (`/`)**:
   * 아무 입력칸에 커서를 두고 키보드 **`/` (슬래시)** 를 누르면 그 자리에 `동적 콘텐츠 삽입` / `식 삽입` 메뉴가 바로 뜹니다.
2. **`⚡` 로 변수 넣기**:
   * 입력칸 클릭 → 옆에 뜨는 번개(`⚡`) 아이콘 클릭 → `Parse JSON` 아래에 나열된 필드(`service`, `env` 등) 클릭 → 파란색/보라색 **알약(칩)** 형태로 삽입됨.
3. **`fx` 로 수식 넣기 (★ 초보자가 가장 편한 방법)**:
   * `fx` 아이콘 클릭 → 수식 입력창이 열림.
   * 수식 이름을 타이핑한 뒤 (예: `join(`, `)` 안쪽에 커서를 둔 상태에서) **수식창 상단/하단의 `동적 콘텐츠` 버튼을 눌러 이전 단계 필드를 클릭**하면 `body('Parse_JSON')?['users']` 같은 긴 경로를 **자동으로 수식 안에 써줍니다!** (직접 외워서 타이핑할 필요 없음)
   * 다 작성했으면 반드시 아래 **`추가 (Add)`** (수정 시 `업데이트 / Update`) 버튼을 눌러야 입력칸에 적용됩니다.

---

### 4.2 수식 문법에서 무조건 알아야 할 4가지 규칙

1. **문자열과 키 이름은 반드시 작은따옴표(`'`) 사용**
   * ❌ `concat("서버: ", body('Parse_JSON')?["service"])` (에러!)
   * ✅ `concat('서버: ', body('Parse_JSON')?['service'])`
2. **액션 이름에 띄어쓰기가 있으면 밑줄(`_`)로 바뀜**
   * 액션 블록 제목이 `Parse JSON` 이면 → 식에서는 `'Parse_JSON'`
   * 액션 블록 제목이 `Create a chat` 이면 → 식에서는 `'Create_a_chat'`
   * *(팁: 액션 제목을 바꾸면 기존 수식이 깨질 수 있으니 처음에 영어 이름으로 짧게 짓는 것을 권장)*
3. **물음표 `?` 는 필수 안전장치 (Null-safe)**
   * `body('Parse_JSON')['user']['email']` → `user`가 없으면 흐름이 에러로 터짐.
   * `body('Parse_JSON')?['user']?['email']` → 키가 없어도 에러 대신 `null` 반환.
4. **`@` 와 `@{...}` 차이**
   * **`fx` 창 안**에서는 `@` 없이 순수 함수만 적습니다: `triggerBody()?['env']`
   * **일반 텍스트 박스나 JSON 텍스트 중간**에 직접 타이핑해서 끼워 넣을 때는 `@{...}` 로 감쌉니다:
     ```json
     "text": "현재 환경은 @{body('Parse_JSON')?['env']} 입니다."
     ```
   * *(실제 UI에서는 `fx` 창으로 넣으면 자동으로 이 칩이 들어갑니다.)*

---

## 5. Step 4: 실무에서 바로 쓰는 수식(`fx`) 백과사전

### 5.1 JSON에서 값 꺼내기 (`Parse JSON` 없이도 가능!)

`Parse JSON` 단계를 생략하고 트리거/액션 결과에서 바로 값을 꺼내는 식입니다.

| 하고 싶은 것 | `fx` 수식 |
| :--- | :--- |
| 트리거가 받은 JSON의 `service` 값 | `triggerBody()?['service']` |
| 중첩 JSON `data.user.name` 값 | `triggerBody()?['data']?['user']?['name']` |
| 값이 `null`이거나 비어있을 때 기본값 지정 | `coalesce(triggerBody()?['env'], 'dev')` |
| 문자열로 온 JSON(`"{\"a\":1}"`)을 객체로 변환 | `json(triggerBody()?['rawPayload'])` |
| `Create a chat`에서 생성된 `Conversation ID` | `outputs('Create_a_chat')?['body/id']` |
| `Wait for a response`에서 클릭한 버튼 ID | `outputs('Post_adaptive_card_and_wait_for_a_response')?['body/submitActionId']` |
| `Wait for a response`에서 사용자가 입력한 값 | `outputs('Post_adaptive_card_and_wait_for_a_response')?['body/data/comment']` |

---

### 5.2 배열(`[]`) 다루기 & "각각에 적용(Apply to each)" 반복문 피하기

Power Automate에서 **배열 필드를 `⚡`로 그냥 클릭하면 자동으로 `Apply to each(각각에 적용)` 루프가 씌워집니다.** 채팅방에 메시지를 1번만 보내고 싶은데 사람 수만큼 3번 전송되는 흔한 원인입니다.

| 하고 싶은 것 | `fx` 수식 |
| :--- | :--- |
| 배열의 **첫 번째 항목**만 꺼내기 (루프 방지) | `first(body('Parse_JSON')?['items'])?['name']` |
| 인덱스(`0`번째)로 특정 요소 꺼내기 | `body('Parse_JSON')?['items']?[0]?['name']` |
| 이메일 배열 `["a@co.com","b@co.com"]` → `;` 합치기 (`Create a chat`용) | `join(body('Parse_JSON')?['users'], ';')` |
| 배열 길이(개수) 확인 | `length(body('Parse_JSON')?['items'])` |

---

### 5.3 문자열 & 한국 시간(KST) 날짜 변환

Power Automate의 `utcNow()` 는 항상 UTC(한국보다 9시간 느림) 기준입니다.

| 하고 싶은 것 | `fx` 수식 |
| :--- | :--- |
| 현재 시각을 **한국 시간(KST)** 포맷으로 출력 | `formatDateTime(convertFromUtc(utcNow(), 'Korea Standard Time'), 'yyyy-MM-dd HH:mm:ss')` |
| ISO 문자열 날짜(`2026-11-17T06:00:00Z`) → KST 변환 | `formatDateTime(convertFromUtc(triggerBody()?['createdAt'], 'Korea Standard Time'), 'MM/dd HH:mm')` |
| 문자열 합치기 | `concat('[', body('Parse_JSON')?['env'], '] ', body('Parse_JSON')?['title'])` |
| 대문자 / 소문자 변환 | `toUpper(body('Parse_JSON')?['env'])` / `toLower(...)` |
| 문자열 자르기 / 포함 여부 | `contains(triggerBody()?['msg'], 'ERROR')` |
| 줄바꿈·큰따옴표가 들어간 문자열을 JSON 안전 이스케이프 | `replace(replace(body('Parse_JSON')?['msg'], '"', '\"'), decodeUriComponent('%0A'), '\n')` |

---

### 5.4 조건식 (`if`, `equals`, `empty`)

| 하고 싶은 것 | `fx` 수식 |
| :--- | :--- |
| 삼항 연산 (`env == 'prod'`면 `'Attention'`, 아니면 `'Good'`) | `if(equals(body('Parse_JSON')?['env'], 'prod'), 'Attention', 'Good')` |
| 값이 비어있는지 확인 (`null`, `""`, `[]` 모두 `true`) | `empty(body('Parse_JSON')?['reason'])` |
| 조건이 참/거짓 중 하나라도 맞으면 (`OR`) | `or(equals(triggerBody()?['env'], 'prod'), equals(triggerBody()?['env'], 'stg'))` |
| 두 조건 모두 만족 (`AND`) | `and(equals(triggerBody()?['env'], 'prod'), greater(triggerBody()?['count'], 5))` |

> [!TIP]
> **Adaptive Card 색상을 동적으로 바꾸는 활용 예**:
> `"color": "` 뒤에 `fx` 로 `if(equals(body('Parse_JSON')?['severity'], 'CRITICAL'), 'Attention', 'Good')` 를 넣으면, 장애 심각도에 따라 카드 제목 색이 자동으로 빨강(`Attention`) 또는 초록(`Good`)으로 바뀝니다!

---

## 6. Step 5: 실전 따라하기 (2가지 대표 시나리오)

### 실습 A: 백엔드 커스텀 JSON → 가공(수식) → 채팅방에 Adaptive Card 띄우기

**목표**: 백엔드가 아래 JSON을 보내면, 한국 시간(KST)을 붙이고 심각도에 따라 색상을 바꿔 그룹 채팅방에 카드를 띄웁니다.

```json
{
  "service": "order-api",
  "severity": "CRITICAL",
  "message": "DB 커넥션 풀 고갈",
  "owners": ["kim@contoso.com", "lee@contoso.com"]
}
```

#### 클릭 순서
1. 대상 그룹 채팅방 `... → 워크플로` → **`채팅에 웹후크 경고 보내기`** 선택 → 이름 입력 후 생성 → URL 복사.
2. **Workflows 앱 → 관리 → 방금 만든 흐름 → `편집 (Edit)`** 클릭.
3. 기본으로 들어있는 **`Send each adaptive card`** 블록의 `... → 삭제` 클릭.
4. 트리거 아래 **`+` → `작업 추가` → `Parse JSON` (JSON 구문 분석)** 검색 및 추가.
   * **Content**: `⚡` 클릭 → `Body` 선택.
   * **샘플 페이로드에서 생성** 클릭 → 위 백엔드 JSON 예시 붙여넣고 완료.
5. `+` → `작업 추가` → **`Post card in a chat or channel` (채팅 또는 채널에 카드 게시)** 선택.
   * **Post as**: `Flow bot`
   * **Post in**: `Group chat`
   * **Group chat**: 알림 받을 채팅방 선택
   * **Adaptive Card** 입력칸에 아래 JSON을 붙여넣으세요. (`@{...}`로 표시된 자리는 그대로 붙여넣거나, 해당 위치를 지우고 `⚡` / `fx` 칩을 직접 삽입하면 됩니다.)

```json
{
  "$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
  "type": "AdaptiveCard",
  "version": "1.4",
  "body": [
    {
      "type": "TextBlock",
      "text": "🚨 [@{body('Parse_JSON')?['severity']}] @{body('Parse_JSON')?['service']}",
      "weight": "Bolder",
      "size": "Medium",
      "color": "@{if(equals(body('Parse_JSON')?['severity'], 'CRITICAL'), 'Attention', 'Accent')}"
    },
    {
      "type": "TextBlock",
      "text": "@{body('Parse_JSON')?['message']}",
      "wrap": true
    },
    {
      "type": "FactSet",
      "facts": [
        {
          "title": "발생 시각(KST)",
          "value": "@{formatDateTime(convertFromUtc(utcNow(), 'Korea Standard Time'), 'yyyy-MM-dd HH:mm:ss')}"
        },
        {
          "title": "담당자",
          "value": "@{join(body('Parse_JSON')?['owners'], ', ')}"
        }
      ]
    }
  ]
}
```
6. 우측 상단 **`저장 (Save)`** 클릭 후, 백엔드(또는 `curl`/Postman)로 테스트 JSON을 `POST` 전송합니다.

> [!NOTE]
> **팁 — 줄바꿈·따옴표 많은 문자열 때문에 JSON이 깨질 때 (`Compose` 우회법)**
> Adaptive Card 입력칸에 직접 JSON 텍스트를 넣고 `@{...}`로 치환할 때, `message` 안에 큰따옴표(`"`)나 엔터(`\n`)가 들어있으면 `Invalid JSON` 에러가 날 수 있습니다.
> 이럴 때는 카드 게시 바로 위에 **`작성 (Compose)`** 액션을 하나 추가해 **JSON을 객체 형태로 조립한 뒤**, `Post card...`의 `Adaptive Card` 칸에는 `⚡`로 `Compose`의 **`Outputs` 단 하나만** 넣으면 Power Automate가 내부 문자열 이스케이프를 자동으로 안전하게 처리해 줍니다!

---

### 실습 B: 버튼 클릭 응답 받아 `Condition` 분기 처리하기

**목표**: 채팅방에 `[승인]` / `[반려]` 버튼이 있는 카드를 보내고, 누가 어떤 버튼을 눌렀는지에 따라 후속 메시지를 다르게 보냅니다.

1. `+` → `Microsoft Teams` → **`Post adaptive card and wait for a response`** 추가.
   * **Post as**: `Flow bot`
   * **Post in**: `Group chat` → 대상 채팅방 선택
   * **Update message**: `응답이 처리되었습니다.`
   * **Should update card**: `Yes`
   * **Message**: 아래 카드 입력 (`Action.Submit` 사용, 버튼마다 고유 `"id"` 지정 필수):
     ```json
     {
       "$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
       "type": "AdaptiveCard",
       "version": "1.4",
       "body": [
         { "type": "TextBlock", "text": "배포 승인 요청 (v2.3.0)", "weight": "Bolder" },
         { "type": "Input.Text", "id": "reason", "label": "코멘트", "isMultiline": true }
       ],
       "actions": [
         { "type": "Action.Submit", "id": "btnApprove", "title": "✅ 승인", "style": "positive" },
         { "type": "Action.Submit", "id": "btnReject", "title": "❌ 반려", "style": "destructive" }
       ]
     }
     ```
2. 카드 아래 `+` → **`조건 (Condition)`** 액션 추가 (`Control` 커넥터).
   * 왼쪽 칸 클릭 → `fx`로 입력:
     ```text
     outputs('Post_adaptive_card_and_wait_for_a_response')?['body/submitActionId']
     ```
   * 비교 연산자: **`다음과 같음 (is equal to)`**
   * 오른쪽 칸: **`btnApprove`**
3. **True (예)** 분기 아래 `+`: `Post message in a chat or channel` 추가 → `"승인 완료 (처리자: "` + `fx`로 `outputs('Post_adaptive_card_and_wait_for_a_response')?['body/responder/displayName']` + `")"` 입력.
4. **False (아니오)** 분기 아래 `+`: `Post message in a chat or channel` 추가 → 반려 사유(`outputs('Post_adaptive_card_and_wait_for_a_response')?['body/data/reason']`) 포함 안내 메시지 전송.

---

## 7. 디버깅 & 자주 겪는 실수 Top 6

### 1) 수식 경로가 맞는지 헷갈릴 때 → "실행 기록(Run history)"이 정답입니다
* 흐름이 한 번이라도 실행(성공 또는 실패)되면 **편집 화면 밖(상세 페이지) → `28일 실행 기록 (28-day run history)`** 에서 해당 실행을 클릭합니다.
* 각 액션 블록을 클릭하면 **`원시 입력(Raw inputs)` / `원시 출력(Raw outputs)`** 에서 실제 JSON 키 이름을 그대로 눈으로 복사할 수 있습니다.
* 출력 JSON을 그대로 복사해서 `Parse JSON → 샘플 페이로드에서 생성`에 넣으면 100% 정확한 스키마가 만들어집니다.

### 2) `InvalidJSON` 오류 — `triggerBody()`를 그냥 타이핑한 경우
* `Parse JSON`의 `Content` 칸에 `triggerBody()`라고 글자만 치면, `"triggerBody()"`라는 일반 문자열을 파싱하려다 실패합니다.
* 반드시 **`⚡`에서 `Body`를 선택**하거나 **`fx` 창에서 입력**해 색깔 있는 알약(칩) 모양으로 들어가야 합니다.

### 3) 갑자기 `Apply to each(각각에 적용)` 상자가 생겼을 때
* `⚡`에서 고른 필드가 배열(`[]`)일 때 Power Automate가 자동으로 감쌉니다.
* **해결**: `Ctrl+Z` 또는 루프 안의 액션을 바깥으로 드래그해 빼낸 뒤 루프를 삭제하고, 해당 필드 자리에 `fx`로 **`first(...)`** 나 **`join(..., ', ')`** 를 써서 단일 값으로 바꿔 넣으세요.

### 4) `BadRequest` — Adaptive Card JSON 문법/버전 에러
* Workflows 커넥터는 **Adaptive Card v1.4** 이하가 가장 안정적입니다. (v1.5/1.6 전용 속성이나 `${...}` 템플릿 문법은 지원하지 않음)
* `Action.Execute` 대신 **`Action.Submit`** 을 사용해야 `Wait for a response`나 `When someone responds...`에서 응답을 받을 수 있습니다.

### 5) 흐름을 복사하거나 액션 이름을 바꾼 뒤 에러 발생
* `outputs('액션_이름')` 은 액션 카드의 **상단 제목**을 그대로 참조합니다.
* 액션 제목을 수정한 뒤에는 그 아래 단계에서 쓰던 `fx` 수식 안의 액션 이름도 같이 업데이트되었는지 확인해야 합니다.

### 6) "승인 대기 카드"가 몇 주 지나도 응답이 안 오고 타임아웃 날 때
* Power Automate 단일 실행(클라우드 흐름)의 기본 최대 실행 대기 시간은 **30일**입니다.
* 30일 이상 대기가 필요하면 `04번 문서`의 **분리형 리스너(`Post card` + `When someone responds to an adaptive card`)** 패턴을 사용하세요.
